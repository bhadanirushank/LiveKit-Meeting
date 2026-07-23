import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main() {
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/v1/meetings"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""
            {
                "title": "Integration Test Meeting",
                "passcode": "123456",
                "waitingRoomEnabled": true,
                "joinBeforeHostEnabled": false,
                "maximumParticipants": 10,
                "idempotencyKey": "${java.util.UUID.randomUUID()}"
            }
        """.trimIndent()))
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    println("Status: ${response.statusCode()}")
    println("Body: ${response.body()}")
}
