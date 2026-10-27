package app.hearth.ai.sense

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import app.hearth.ai.route.MeetingWords
import app.hearth.ai.route.Place
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.LongBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sentence model end to end, as the tablet runs it (ONNX Runtime): the same meaning as the reference, and a
 * conversation read with it. Runs where the model is at hand (HEARTH_SENSE_DIR); the rules are tested everywhere.
 */
class SenseModelTest {
    private val dir = System.getenv("HEARTH_SENSE_DIR")?.let(::File)?.takeIf { File(it, "onnx/model_quantized.onnx").exists() }

    private fun model(): SenseModel {
        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession(File(dir, "onnx/model_quantized.onnx").path)
        val encoder = SentenceEncoder { ids ->
            val shape = longArrayOf(1, ids.size.toLong())
            fun tensor(values: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
            val inputs = mapOf(
                "input_ids" to tensor(LongArray(ids.size) { ids[it].toLong() }),
                "attention_mask" to tensor(LongArray(ids.size) { 1 }),
                "token_type_ids" to tensor(LongArray(ids.size)),
            )
            session.run(inputs).use { r ->
                @Suppress("UNCHECKED_CAST")
                val h = (r[0].value as Array<Array<FloatArray>>)[0]
                FloatArray(h.size * h[0].size) { h[it / h[0].size][it % h[0].size] }
            }
        }
        return SenseModel(UnigramTokenizer(UnigramTokenizer.parseVocab(File(dir, "tokenizer.json").readText())), encoder)
    }

    @Test
    fun theSameMeaningAsTheReference() = runTest {
        dir ?: return@runTest
        val reference = File(dir, "ref.txt").readText().trim().split(" ").map { it.toFloat() }
        val v = model().embed("de keuken staat altijd vol")
        val cosine = v.indices.sumOf { (v[it] * reference[it]).toDouble() }
        assertTrue(cosine > 0.999, "cosine $cosine")
    }

    @Test
    fun aConversationReadWithTheModel() = runTest {
        dir ?: return@runTest
        val m = model().also { it.train() }
        // Phrased in ways no rule knows.
        val cases = mapOf(
            "ik ben het zat dat de badkamer nooit gepoetst wordt" to Sense.FRICTION,
            "volgende week maandag moet de kleine naar de pediater" to Sense.EVENT,
            "da's goe voor mij" to Sense.AGREE,
            "elke vrijdagavond de keuken grondig poetsen" to Sense.ROUTINE,
        )
        val read = cases.mapValues { (text, _) -> m.read(text).sense }
        assertEquals(cases, read)

        // The weekoverleg with the model's readings: what it reads with confidence, it decides.
        val readings = cases.keys.associateWith { m.read(it) }
        val t = MeetingWords.thoughts(listOf("ik ben het zat dat de badkamer nooit gepoetst wordt"), LocalDate(2026, 10, 25), sense = { readings[it] })
        assertEquals(listOf(Place.FRICTION), t.flatMap { it.conclusions }.map { it.place })
    }

    @Test
    fun theTwoPersonWeekoverlegStillReadsTheSame() = runTest {
        dir ?: return@runTest
        val m = model().also { it.train() }
        val heard = listOf(
            "oké hoe was jouw week eigenlijk", "ja best druk op het werk maar ik vond het wel goed dat we woensdag samen gegeten hebben",
            "ja dat was fijn", "ik was wel moe donderdag", "ik heb eerlijk gezegd de was niet goed opgevolgd deze week",
            "nee dat ligt nog allemaal in de mand", "ik denk dat ik het gewoon altijd uitstel tot het weekend", "mja dat herken ik wel",
            "wat staat er volgende week eigenlijk op de planning", "dinsdag heeft Mia tandarts", "en ik moet donderdag de auto naar de garage brengen",
            "misschien kan jij Mia dan ophalen", "ja dat kan ik doen", "wat ik niet zo leuk vind is dat de keuken 's avonds altijd vol staat",
            "ja dat stoort mij ook", "zouden we niet kunnen afspreken dat wie kookt niet afwast",
            "hmm of misschien dat we gewoon voor het slapengaan samen tien minuten opruimen", "ik weet niet of dat gaat lukken elke dag",
            "wat als we het alleen op weekdagen doen", "ja maar dan blijft het weekend een rommel", "oké dus dan elke avond maar maximaal tien minuten",
            "ja dat lijkt me haalbaar", "goed dan spreken we af dat we elke avond voor het slapengaan tien minuten samen de keuken opruimen", "afgesproken",
        )
        val today = LocalDate(2026, 10, 25)
        // Read every clause the way the screen does: a first pass asks, the model reads, the second pass uses it.
        val asked = mutableSetOf<String>()
        MeetingWords.thoughts(heard, today, listOf("Mia"), sense = { asked += it; null })
        val readings = asked.associateWith { m.read(it) }
        val places = MeetingWords.thoughts(heard, today, listOf("Mia"), sense = { readings[it] }).flatMap { t -> t.conclusions.map { it.place } }
        // The car to the garage is a task (the rules made it an appointment).
        assertEquals(listOf(Place.WIN, Place.EVENT, Place.PLANNING, Place.EVENT, Place.FRICTION, Place.HOUSE_RULE), places)
    }
}
