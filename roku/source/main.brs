' Ultra Premier TV para Roku
' Punto de entrada: abre la escena principal y espera a que se cierre.

sub Main(args as Dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    scene = screen.CreateScene("MainScene")
    screen.Show()

    while true
        msg = Wait(0, port)
        if Type(msg) = "roSGScreenEvent"
            if msg.IsScreenClosed() then return
        end if
    end while
end sub
