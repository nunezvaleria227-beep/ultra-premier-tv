sub init()
    m.grid = m.top.findNode("grid")
    m.clock = m.top.findNode("clock")
    m.account = m.top.findNode("account")
    m.tick = m.top.findNode("tick")

    root = CreateObject("roSGNode", "ContentNode")
    addTile(root, "live", "TV en directo", "pkg:/images/tile_live.jpg")
    addTile(root, "movie", "Películas", "pkg:/images/tile_movies.jpg")
    addTile(root, "series", "Series", "pkg:/images/tile_series.jpg")
    addTile(root, "search", "Búsqueda", "pkg:/images/tile_search.jpg")
    addTile(root, "continue", "Continuar viendo", "pkg:/images/tile_continue.jpg")
    addTile(root, "settings", "Ajustes", "pkg:/images/tile_settings.jpg")
    m.grid.content = root
    m.grid.observeField("itemSelected", "onSelected")

    m.tick.observeField("fire", "refreshInfo")
    m.tick.control = "start"
    refreshInfo()
end sub

sub addTile(root as Object, id as String, title as String, image as String)
    n = root.CreateChild("ContentNode")
    n.id = id
    n.title = title
    n.ShortDescriptionLine1 = title
    n.HDPosterUrl = image
end sub

sub focusScreen()
    refreshInfo()
    m.grid.setFocus(true)
end sub

sub refreshInfo()
    now = nowSecs()
    m.clock.text = fmtHM(now)
    s = m.global.session
    if s = invalid or s.account = invalid then return
    acc = s.account
    line = "Usuario: " + toS(s.user)
    if acc.exp > 0
        days = (acc.exp - now) \ 86400
        line = line + Chr(10) + "Vence: " + fmtDateEs(acc.exp)
        if days >= 0 then line = line + " (" + days.ToStr() + " días)"
    end if
    m.account.text = line
end sub

sub onSelected(event as Object)
    index = event.getData()
    node = m.grid.content.getChild(index)
    m.top.event = { type: "open", target: node.id }
end sub

' ----------------------------------------------------------------- tecla * (opciones): cerrar sesión

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "options"
        showOptions()
        return true
    end if
    return false
end function

sub showOptions()
    dlg = CreateObject("roSGNode", "Dialog")
    dlg.title = "Opciones"
    s = m.global.session
    if s <> invalid and s.user <> invalid then dlg.message = "Cuenta: " + toS(s.user)
    dlg.buttons = ["Cerrar sesión", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onOptionsChoice")
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onOptionsChoice()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    dlg.close = true
    m.dialog = invalid
    m.grid.setFocus(true)
    if choice = 0 then m.top.event = { type: "logout" }
end sub
