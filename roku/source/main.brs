' Ultra Premier TV para Roku
' Punto de entrada: abre la escena principal, le pasa los enlaces directos (deep linking) y espera a que se cierre.
' También vigila la memoria de la app (Roku lo exige para la certificación).

sub Main(args as Dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    input = CreateObject("roInput")
    input.SetMessagePort(port)
    mon = startMemoryMonitor(port)
    scene = screen.CreateScene("MainScene")
    screen.Show()
    logMemory(mon, "inicio")

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
        else if t = "roAppMemoryNotificationEvent"
            ' La app usa mucha memoria: la escena suelta las listas guardadas.
            logMemory(mon, "aviso de memoria")
            scene.memoryWarning = true
        else if t = "roDeviceInfoEvent"
            ' Roku más antiguo: aviso general de poca memoria.
            info = msg.GetInfo()
            if info <> invalid and info.generalMemoryLevel <> invalid
                if info.generalMemoryLevel <> "normal" then scene.memoryWarning = true
            end if
        end if
    end while
end sub

' Activa los avisos de memoria de Roku. Devuelve el monitor (o invalid si este Roku no lo tiene).
function startMemoryMonitor(port as Object) as Dynamic
    mon = CreateObject("roAppMemoryMonitor")
    if mon <> invalid
        mon.SetMessagePort(port)
        if mon.EnableMemoryWarningEvent(true) then return mon
    end if
    ' Respaldo para Rokus sin el monitor nuevo: aviso general de poca memoria.
    m.deviceInfo = CreateObject("roDeviceInfo")
    m.deviceInfo.SetMessagePort(port)
    m.deviceInfo.EnableLowGeneralMemoryEvent(true)
    return mon
end function

' Escribe en la consola de depuración cuánta memoria usa la app (solo sirve a quien la prueba en modo desarrollador).
sub logMemory(mon as Dynamic, label as String)
    if mon = invalid then return
    limits = mon.GetChannelMemoryLimit()
    maxForeground = 0
    if limits <> invalid and limits.maxForegroundMemory <> invalid then maxForeground = limits.maxForegroundMemory
    print "[memoria] "; label; " uso="; mon.GetMemoryLimitPercent(); "% libre(KB)="; mon.GetChannelAvailableMemory(); " limite="; maxForeground
end sub

sub sendDeepLink(scene as Object, info as Dynamic)
    if info = invalid then return
    if Type(info) <> "roAssociativeArray" then return
    if info.DoesExist("contentId") and info.DoesExist("mediaType")
        scene.deepLink = { contentId: info.contentId, mediaType: info.mediaType }
    end if
end sub
