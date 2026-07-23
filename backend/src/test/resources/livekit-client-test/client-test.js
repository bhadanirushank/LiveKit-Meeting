import { Room, RoomEvent } from "@livekit/rtc-node";

async function main() {
    let input = "";
    for await (const chunk of process.stdin) {
        input += chunk;
    }

    let config;
    try {
        config = JSON.parse(input);
    } catch (e) {
        console.error("Failed to parse stdin as JSON");
        process.exit(1);
    }

    const { url, tokenA, tokenB, roomName } = config;

    if (!url || !tokenA || !tokenB || !roomName) {
        console.error("Missing required fields in input JSON");
        process.exit(1);
    }

    const roomA = new Room();
    const roomB = new Room();

    let clientBSeenByA = false;
    let clientASeenByB = false;
    let dataMessageReceived = false;

    roomA.on(RoomEvent.ParticipantConnected, (participant) => {
        clientBSeenByA = true;
    });
    roomB.on(RoomEvent.ParticipantConnected, (participant) => {
        clientASeenByB = true;
    });

    roomB.on(RoomEvent.DataReceived, (payload, participant, kind, topic) => {
        const message = new TextDecoder().decode(payload);
        if (message === "HELLO_FROM_A") {
            dataMessageReceived = true;
        }
    });

    const timeoutMs = 15000;
    const timeoutPromise = new Promise((_, reject) => {
        setTimeout(() => reject(new Error("Test timed out")), timeoutMs);
    });

    try {
        await Promise.race([
            (async () => {
                await roomA.connect(url, tokenA);
                await roomB.connect(url, tokenB);

                if (roomA.remoteParticipants.size > 0) clientBSeenByA = true;
                if (roomB.remoteParticipants.size > 0) clientASeenByB = true;

                while (!clientASeenByB || !clientBSeenByA) {
                    await new Promise(resolve => setTimeout(resolve, 100));
                }

                const payload = new TextEncoder().encode("HELLO_FROM_A");
                await roomA.localParticipant.publishData(payload, {
                    reliable: true,
                    topic: "test-topic"
                });

                while (!dataMessageReceived) {
                    await new Promise(resolve => setTimeout(resolve, 100));
                }

                console.log("SUCCESS");
            })(),
            timeoutPromise
        ]);
    } catch (e) {
        console.error("Test failed: " + e.message);
        process.exit(1);
    } finally {
        try {
            await roomA.disconnect();
            await roomB.disconnect();
            
            setTimeout(() => {
                process.exit(0);
            }, 500);
        } catch (cleanupErr) {
            console.error("Cleanup failed");
            process.exit(1);
        }
    }
}

main().catch(err => {
    console.error("Fatal error");
    process.exit(1);
});

