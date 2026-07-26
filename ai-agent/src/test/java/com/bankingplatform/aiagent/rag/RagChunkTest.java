package com.bankingplatform.aiagent.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagChunkTest {

    @Test
    void cosineIsOneForIdenticalVectors() {
        float[] a = {1f, 0f, 0f};
        assertEquals(1.0, RagChunk.cosine(a, a), 1e-6);
    }

    @Test
    void cosineIsZeroForOrthogonalVectors() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(0.0, RagChunk.cosine(a, b), 1e-6);
    }

    @Test
    void cosineHandlesNullSafely() {
        assertEquals(0.0, RagChunk.cosine(null, new float[]{1f}), 1e-6);
        assertTrue(RagChunk.cosine(new float[]{1f, 2f}, new float[]{2f, 4f}) > 0.99);
    }
}
