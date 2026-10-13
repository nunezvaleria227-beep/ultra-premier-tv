sub init()
    m.list = m.top.findNode("fields")
    m.errorLabel = m.top.findNode("error")
    m.status = m.top.findNode("status")
    m.autoTimer = m.top.findNode("autoTimer")
    m.tasks = []
    m.busy = false
    ' "manual" = la persona pulsó Entrar (no fue la entrada automática con la sesión guardada).
    m.manual = false
    m.user = regRead("user")
    m.pass = regRead("pass")

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
    dlg.buttons = ["Aceptar", "Cancelar"]
    if secure and dlg.textEditBox <> invalid then dlg.textEditBox.secureMode = true
    dlg.observeFieldScoped("buttonSelected", "onKeyboardDone")
    m.dialog = dlg
    m.which = which
    m.top.getScene().dialog = dlg
end sub

sub onKeyboardDone()
    dlg = m.dialog
    if dlg = invalid then return
    if dlg.buttonSelected = 0
        value = dlg.text
        if m.which = "user" then m.user = value.Trim()
        if m.which = "pass" then m.pass = value
        refreshList()
    end if
    dlg.close = true
    m.dialog = invalid
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
        m.top.event = { type: "loggedIn", user: m.user, pass: m.pass, account: resp.account, liveExt: resp.liveExt, manual: manual }
    else
        setError(resp.error)
        m.list.setFocus(true)
    end if
end sub
