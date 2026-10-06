package com.example

import com.example.data.local.entity.Plant
import com.example.util.PlantVisualMatcher
import org.junit.Assert.*
import org.junit.Test

class PlantTrackUnitTest {

    @Test
    fun testPlantModelCreation() {
        val plant = Plant(
            id = "PLANT-0001",
            name = "Test Mint",
            species = "Mentha",
            notes = "Healthy indoor sample"
        )
        assertEquals("PLANT-0001", plant.id)
        assertEquals("Test Mint", plant.name)
        assertFalse(plant.isArchived)
    }

    @Test
    fun testCandidateMatchStructure() {
        val plant = Plant(id = "PLANT-0001", name = "Basil")
        val match = PlantVisualMatcher.CandidateMatch(plant = plant, confidence = 0.94f)
        assertEquals(0.94f, match.confidence, 0.001f)
        assertTrue(match.confidence >= 0.90f)
    }
}
