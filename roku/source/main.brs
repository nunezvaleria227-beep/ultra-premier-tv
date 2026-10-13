' Ultra Premier TV para Roku
' Punto de entrada: abre la escena principal, le pasa los enlaces directos (deep linking) y espera a que se cierre.

sub Main(args as Dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    input = CreateObject("roInput")
    input.SetMessagePort(port)
    scene = screen.CreateScene("MainScene")
    screen.Show()

    ' Enlace directo con el que se abrió el canal (si lo hay).
    sendDeepLink(scene, args)

    while true
        msg = Wait(0, port)
        t = Type(msg)
        if t = "roSGScreenEvent"
            if msg.IsScreenClosed() then return
        else if t = "roInputEvent"
            ' Enlace directo con el canal ya abierto.
            sendDeepLink(scene, msg.GetInfo())
        end if
    end while
end sub

sub sendDeepLink(scene as Object, info as Dynamic)
    if info = invalid then return
    if Type(info) <> "roAssociativeArray" then return
    if info.DoesExist("contentId") and info.DoesExist("mediaType")
        scene.deepLink = { contentId: info.contentId, mediaType: info.mediaType }
    end if
end sub
