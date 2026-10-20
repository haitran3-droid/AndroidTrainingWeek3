package com.example.dogcattranslator.media

import com.example.dogcattranslator.data.model.AmplitudeLevel
import com.example.dogcattranslator.data.model.AnimalType
import com.example.dogcattranslator.data.repository.DemoMediaRepository
import org.junit.Assert.*
import org.junit.Test

class AmplitudeAnalyzerTest {
    @Test
    fun emptySamplesAreSilentWithoutDivisionByZero() {
        val result = AmplitudeAnalyzer().result()
        assertEquals(0.0, result.averagePeak, 0.0)
        assertEquals(0, result.sampleCount)
        assertEquals(AmplitudeLevel.SILENT, result.level)
    }

    @Test
    fun calculatesArithmeticMeanOfValidPeaks() {
        val analyzer = AmplitudeAnalyzer()
        listOf(-1, 0, 1000, 2000, 3000).forEach(analyzer::addSample)
        assertEquals(1500.0, analyzer.result().averagePeak, 0.0)
        assertEquals(4, analyzer.result().sampleCount)
        assertEquals(AmplitudeLevel.LOW, analyzer.result().level)
    }

    @Test
    fun thresholdBoundariesUseExpectedLevels() {
        val expected =
            mapOf(
                0 to AmplitudeLevel.SILENT,
                499 to AmplitudeLevel.SILENT,
                500 to AmplitudeLevel.LOW,
                4999 to AmplitudeLevel.LOW,
                5000 to AmplitudeLevel.MEDIUM,
                9999 to AmplitudeLevel.MEDIUM,
                10000 to AmplitudeLevel.HIGH,
                14999 to AmplitudeLevel.HIGH,
                15000 to AmplitudeLevel.HIGH,
            )
        expected.forEach { (peak, level) ->
            val analyzer = AmplitudeAnalyzer().also { it.addSample(peak) }
            assertEquals("peak=$peak", level, analyzer.result().level)
        }
    }

    @Test
    fun manyPeaksDoNotOverflowIntSum() {
        val analyzer = AmplitudeAnalyzer()
        repeat(200000) { analyzer.addSample(32767) }
        assertEquals(32767.0, analyzer.result().averagePeak, 0.0)
    }

    @Test
    fun speciesMappingNeverReturnsAnotherSpeciesOrSilentSound() {
        AnimalType.entries.forEach { type ->
            assertNull(DemoMediaRepository.soundFor(type, AmplitudeLevel.SILENT))
            listOf(AmplitudeLevel.LOW, AmplitudeLevel.MEDIUM, AmplitudeLevel.HIGH).forEach { level
                ->
                val sound = requireNotNull(DemoMediaRepository.soundFor(type, level))
                assertEquals(type, sound.animalType)
                assertEquals(level, sound.amplitudeLevel)
            }
        }
    }
}
