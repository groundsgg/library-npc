package gg.grounds.npc.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JavaConsumerTest {
    @Test void javaCanDecodeAndSampleWithoutSerializerOrSceneTypes() {
        NpcDecodeResult result = NpcModelJson.decode(NpcCoreTest.Companion.fixture().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertInstanceOf(NpcDecodeResult.Success.class, result);
        NpcModel model = ((NpcDecodeResult.Success) result).getModel();
        NpcPose pose = NpcPoseSampler.sample(model, "wave", 0.5);
        assertEquals(1.0, pose.getParts().getFirst().point(NpcVector3.ZERO).getX(), 1e-9);
    }
}
