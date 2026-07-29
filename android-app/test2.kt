import io.livekit.android.room.participant.LocalParticipant

fun main() {
    val methods = LocalParticipant::class.java.methods.filter { it.name == "setCameraEnabled" }
    for (m in methods) {
        println(m.name)
        for (p in m.parameterTypes) {
            println(" - param: " + p.name)
        }
    }
}
