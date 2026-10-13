sub init()
    m.list = m.top.findNode("fields")
    m.errorLabel = m.top.findNode("error")
    m.status = m.top.findNode("status")
    m.lastTry = m.top.findNode("lastTry")
    m.autoTimer = m.top.findNode("autoTimer")
    m.tasks = []
    m.busy = false
    ' "manual" = la persona pulsó Entrar (no fue la entrada automática con la sesión guardada).
    m.manual = false
    m.dbgUser = "-"
    m.dbgPass = "-"
    m.user = regRead("user")
    m.pass = regRead("pass")
    ' Si el intento anterior no funcionó, se deja a la vista el motivo (ayuda a corregir y a pedir soporte).
    lastTry = regRead("lastTry")
    if lastTry <> "" then m.lastTry.text = "Último intento: " + lastTry

    root = CreateObject("roSGNode", "ContentNode")
    root.CreateChild("ContentNode")
    root.CreateChild("ContentNode")
    root.CreateChild("ContentNode")
    m.list.content = root
    refreshList()
    m.list.observeField("itemSelected", "onSelected")

    ' Si ya había una sesión guardada, entra sola.
    if m.user <> "" and m.pass <> ""
        m.status.text = "Entrando… un momento"
        m.autoTimer.observeField("fire", "onAutoLogin")
        m.autoTimer.control = "start"
    end if
end sub

sub focusScreen()
    m.list.setFocus(true)
end sub

sub refreshList()
    c = m.list.content
    c.getChild(0).title = "Usuario:   " + m.user
    c.getChild(1).title = "Contraseña:   " + String(Len(m.pass), "*")
    c.getChild(2).title = "Entrar"
end sub

sub onAutoLogin()
    submit()
end sub

sub onSelected(event as Object)
    index = event.getData()
    if index = 0
        askText("Usuario", m.user, false, "user")
    else if index = 1
        askText("Contraseña", m.pass, true, "pass")
    else
        m.manual = true
        submit()
    end if
end sub

sub askText(titleText as String, current as String, secure as Boolean, which as String)
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = titleText
    dlg.text = current
    ' Un solo botón: Atrás también cierra el teclado y lo escrito se conserva (así también se puede llenar con pruebas automáticas).
    dlg.buttons = ["Aceptar"]
    if secure and dlg.textEditBox <> invalid then dlg.textEditBox.secureMode = true
    m.dialog = dlg
    m.which = which
    ' El texto se va guardando mientras se escribe.
    dlg.observeFieldScoped("text", "onKeyboardText")
    if dlg.textEditBox <> invalid then dlg.textEditBox.observeFieldScoped("text", "onKeyboardText")
    dlg.observeFieldScoped("buttonSelected", "onKeyboardDone")
    dlg.observeFieldScoped("wasClosed", "onKeyboardClosed")
    m.top.getScene().dialog = dlg
end sub

' Lee lo escrito en el teclado (de la caja de texto) y lo guarda. También anota cuántos caracteres veía cada fuente.
sub readKeyboard()
    dlg = m.dialog
    if dlg = invalid then return
    value = toS(dlg.text)
    dlgLen = Len(value)
    boxLen = -1
    editBox = dlg.textEditBox
    if editBox <> invalid
        boxText = editBox.text
        if boxText <> invalid
            value = boxText
            boxLen = Len(boxText)
        end if
    end if
    note = boxLen.ToStr() + "/" + dlgLen.ToStr()
    if m.which = "user"
        m.user = value.Trim()
        m.dbgUser = note
    else
        m.pass = value
        m.dbgPass = note
    end if
end sub

sub onKeyboardText()
    readKeyboard()
end sub

sub onKeyboardDone()
    dlg = m.dialog
    if dlg = invalid then return
    readKeyboard()
    m.dialog = invalid
    dlg.close = true
    refreshList()
    m.list.setFocus(true)
end sub

sub onKeyboardClosed()
    if m.dialog <> invalid
        readKeyboard()
        m.dialog = invalid
    end if
    refreshList()
    m.list.setFocus(true)
end sub

sub setError(text as String)
    m.errorLabel.text = text
end sub

sub submit()
    if m.busy then return
    if m.user = "" or m.pass = ""
        m.status.text = ""
        setError("Escribe tu usuario y tu contraseña.")
        return
    end if
    m.busy = true
    setError("")
    m.status.text = "Entrando… un momento"
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "login", user: m.user, pass: m.pass }
    t.observeField("response", "onLogin")
    m.tasks.Push(t)
    t.control = "RUN"
end sub

sub onLogin(event as Object)
    resp = event.getData()
    m.busy = false
    m.status.text = ""
    manual = m.manual
    m.manual = false
    if resp.ok = true
        if regRead("lastTry") <> "" then regDelete("lastTry")
        m.lastTry.text = ""
        m.top.event = { type: "loggedIn", user: m.user, pass: m.pass, account: resp.account, liveExt: resp.liveExt, manual: manual }
    else
        setError(resp.error)
        userLen = Len(m.user)
        passLen = Len(m.pass)
        diag = "usuario " + Chr(34) + m.user + Chr(34) + " (" + userLen.ToStr() + "), contraseña " + passLen.ToStr() + ". " + toS(resp.diag) + ". Teclado u " + m.dbgUser + ", c " + m.dbgPass
        regWrite("lastTry", diag)
        m.lastTry.text = "Último intento: " + diag
        m.list.setFocus(true)
        m.top.event = { type: "loginFailed" }
    end if
end sub
