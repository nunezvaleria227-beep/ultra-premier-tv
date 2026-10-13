sub init()
    m.form = m.top.findNode("form")
    m.status = m.top.findNode("status")
    m.resultsList = m.top.findNode("results")
    m.query = ""
    m.scopes = ["all", "live", "movie", "series"]
    m.scopeLabels = ["Todo", "Canales", "Películas", "Series"]
    m.scopeIndex = 0
    m.results = []
    m.shownCount = 0
    m.task = invalid
    m.running = false
    m.pane = "form"

    root = CreateObject("roSGNode", "ContentNode")
    root.CreateChild("ContentNode")
    root.CreateChild("ContentNode")
    root.CreateChild("ContentNode")
    m.form.content = root
    refreshForm()
    m.form.observeField("itemSelected", "onFormSelected")
    m.resultsList.observeField("itemSelected", "onResultSelected")
end sub

sub focusScreen()
    if m.pane = "results" and m.shownCount > 0
        m.resultsList.setFocus(true)
    else
        m.pane = "form"
        m.form.setFocus(true)
    end if
end sub

sub onClose()
    cancelSearch()
end sub

sub cancelSearch()
    if m.task <> invalid
        m.task.cancel = true
    end if
    m.running = false
end sub

sub refreshForm()
    c = m.form.content
    c.getChild(0).title = "Texto:   " + m.query
    c.getChild(1).title = "Buscar en:   " + m.scopeLabels[m.scopeIndex]
    c.getChild(2).title = "Buscar"
end sub

sub onFormSelected(event as Object)
    index = event.getData()
    if index = 0
        dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
        dlg.title = "¿Qué quieres buscar?"
        dlg.text = m.query
        dlg.buttons = ["Aceptar", "Cancelar"]
        dlg.observeFieldScoped("buttonSelected", "onKeyboardDone")
        m.dialog = dlg
        m.top.getScene().dialog = dlg
    else if index = 1
        m.scopeIndex = (m.scopeIndex + 1) mod m.scopes.Count()
        refreshForm()
    else
        startSearch()
    end if
end sub

sub onKeyboardDone()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    if choice = 0 then m.query = dlg.text.Trim()
    dlg.close = true
    m.dialog = invalid
    refreshForm()
    m.form.setFocus(true)
    if choice = 0 and Len(m.query) >= 2 then startSearch()
end sub

sub startSearch()
    if Len(m.query) < 2
        m.status.text = "Escribe al menos 2 letras."
        return
    end if
    cancelSearch()
    m.results = []
    m.shownCount = 0
    m.resultsList.content = CreateObject("roSGNode", "ContentNode")
    m.status.text = "Buscando…"
    t = CreateObject("roSGNode", "SearchTask")
    t.query = m.query
    t.scope = m.scopes[m.scopeIndex]
    t.observeField("results", "onResults")
    t.observeField("progress", "onProgress")
    t.observeField("finished", "onFinished")
    m.task = t
    m.running = true
    t.control = "RUN"
end sub

sub onProgress(event as Object)
    if m.running then m.status.text = event.getData()
end sub

sub onFinished()
    if m.task <> invalid then m.status.text = m.task.progress
    m.running = false
end sub

sub onResults(event as Object)
    arr = event.getData()
    if arr = invalid then return
    m.results = arr
    root = m.resultsList.content
    n = arr.Count()
    for i = m.shownCount to n - 1
        e = arr[i]
        kindLabel = "Serie"
        if e.kind = "live" then kindLabel = "Canal"
        if e.kind = "movie" then kindLabel = "Película"
        node = root.CreateChild("ContentNode")
        node.title = kindLabel + "  ·  " + e.name
    end for
    m.shownCount = n
end sub

sub onResultSelected(event as Object)
    index = event.getData()
    if index < 0 or index >= m.results.Count() then return
    e = m.results[index]
    item = { id: e.id, name: e.name, icon: e.icon, ext: e.ext }
    if e.kind = "live"
        m.top.event = { type: "playLive", items: [item], index: 0 }
    else if e.kind = "movie"
        m.top.event = { type: "openMovie", item: item }
    else
        m.top.event = { type: "openSeries", item: item }
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "right" and m.pane = "form" and m.shownCount > 0
        m.pane = "results"
        m.resultsList.setFocus(true)
        return true
    end if
    if (key = "left" or key = "back") and m.pane = "results"
        m.pane = "form"
        m.form.setFocus(true)
        return true
    end if
    return false
end function
