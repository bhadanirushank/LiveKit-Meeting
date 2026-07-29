import io.livekit.android.events.RoomEvent

fun main() {
    val classes = RoomEvent::class.sealedSubclasses
    for (c in classes) {
        println(c.simpleName)
    }
}
