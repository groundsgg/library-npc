package gg.grounds.npc.core;

import static org.junit.jupiter.api.Assertions.*;

import gg.grounds.npc.core.animation.NpcPose;
import gg.grounds.npc.core.animation.NpcPoseSampler;
import gg.grounds.npc.core.io.NpcDecodeResult;
import gg.grounds.npc.core.io.NpcModelJson;
import gg.grounds.npc.core.math.NpcVector3;
import gg.grounds.npc.core.model.NpcModel;
import org.junit.jupiter.api.Test;

class JavaConsumerTest {
  @Test
  void javaCanDecodeAndSampleWithoutSerializerOrSceneTypes() {
    NpcDecodeResult result =
        NpcModelJson.decode(
            NpcCoreTest.Companion.fixture().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertInstanceOf(NpcDecodeResult.Success.class, result);
    NpcModel model = ((NpcDecodeResult.Success) result).getModel();
    NpcPose pose = NpcPoseSampler.sample(model, "wave", 0.5);
    assertEquals(1.0, pose.getParts().getFirst().point(NpcVector3.ZERO).getX(), 1e-9);
  }
}
