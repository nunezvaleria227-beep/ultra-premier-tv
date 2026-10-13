sub init()
    m.list = m.top.findNode("list")
    m.status = m.top.findNode("status")
    m.ids = []
    m.pinPurpose = ""
    m.list.observeField("itemSelected", "onSelected")
    rebuild()
end sub

sub focusScreen()
    m.list.setFocus(true)
end sub

sub addRow(root as Object, id as String, title as String)
    root.CreateChild("ContentNode").title = title
    m.ids.Push(id)
end sub

function parentalOn() as Boolean
    s = m.global.session
    return (s <> invalid and s.parental = true)
end function

function subLabel() as String
    pref = regRead("subpref")
    if pref = "en" then return "English"
    if pref = "off" then return "Ninguno"
    return "Español"
end function

sub rebuild()
    previous = m.list.itemFocused
    root = CreateObject("roSGNode", "ContentNode")
    m.ids = []
    s = m.global.session
    addRow(root, "none", "Cuenta:   " + toS(s.user))
    if s.account <> invalid and s.account.exp > 0
        days = (s.account.exp - nowSecs()) \ 86400
        line = "Vence:   " + fmtDateEs(s.account.exp)
        if days >= 0 then line = line + "  (" + days.ToStr() + " días)"
        addRow(root, "none", line)
        addRow(root, "none", "Conexiones permitidas:   " + toI(s.account.maxCon).ToStr())
    end if
    if parentalOn()
        addRow(root, "parental", "Control parental:   Activado (oculta contenido para adultos)")
    else
        addRow(root, "parental", "Control parental:   Desactivado")
    end if
    addRow(root, "pin", "Crear o cambiar el PIN")
    addRow(root, "subs", "Subtítulos preferidos:   " + subLabel())
    addRow(root, "clear", "Borrar la lista de «Continuar viendo»")
    addRow(root, "logout", "Cerrar sesión")
    addRow(root, "none", "Ultra Premier TV para Roku, versión " + appVersion())
    m.list.content = root
    if previous > 0 and previous < m.ids.Count() then m.list.jumpToItem = previous
end sub

sub onSelected(event as Object)
    index = event.getData()
    if index < 0 or index >= m.ids.Count() then return
    id = m.ids[index]
    if id = "parental"
        toggleParental()
    else if id = "pin"
        if regRead("pin") <> ""
            askPin("Escribe tu PIN actual", "verifyChange")
        else
            askPin("Crea un PIN de 4 números", "newPin")
        end if
    else if id = "subs"
        pref = regRead("subpref")
        if pref = "" or pref = "es"
            regWrite("subpref", "en")
        else if pref = "en"
            regWrite("subpref", "off")
        else
            regWrite("subpref", "es")
        end if
        rebuild()
    else if id = "clear"
        regDelete("continue")
        m.status.text = "Listo: se borró la lista de «Continuar viendo»."
    else if id = "logout"
        m.top.event = { type: "logout" }
    end if
end sub

sub setParental(value as Boolean)
    s = m.global.session
    s.parental = value
    m.global.session = s
    if value
        regWrite("parental", "1")
    else
        regWrite("parental", "0")
    end if
    m.status.text = "Listo. Se aplica la próxima vez que abras una lista de canales, películas o series."
    rebuild()
end sub

sub toggleParental()
    if parentalOn()
        if regRead("pin") <> ""
            askPin("Escribe tu PIN para desactivar el control parental", "disable")
        else
            setParental(false)
            m.status.text = "Control parental desactivado. Te recomiendo crear un PIN para protegerlo."
        end if
    else
        setParental(true)
    end if
end sub

sub askPin(titleText as String, purpose as String)
    dlg = CreateObject("roSGNode", "PinDialog")
    dlg.title = titleText
    dlg.buttons = ["Aceptar", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onPinDone")
    m.pinPurpose = purpose
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onPinDone()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    pin = toS(dlg.pin)
    dlg.close = true
    m.dialog = invalid
    purpose = m.pinPurpose
    m.pinPurpose = ""
    m.list.setFocus(true)
    if choice <> 0 then return
    if purpose = "disable"
        if pin = regRead("pin")
            setParental(false)
        else
            m.status.text = "PIN incorrecto."
        end if
    else if purpose = "verifyChange"
        if pin = regRead("pin")
            askPin("Escribe el nuevo PIN (4 números)", "newPin")
        else
            m.status.text = "PIN incorrecto."
        end if
    else if purpose = "newPin"
        if Len(pin) = 4
            regWrite("pin", pin)
            m.status.text = "PIN guardado."
        else
            m.status.text = "El PIN debe tener 4 números."
        end if
    end if
end sub
