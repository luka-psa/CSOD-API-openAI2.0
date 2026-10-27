@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.web

import app.hearth.ai.sense.SenseRuntime
import app.hearth.ai.sense.SentenceEncoder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The sentence model in the browser: ONNX Runtime Web (loaded only once smarter listening is switched on) runs the model,
 * and the model and its tokenizer are kept in the browser's own cache, so they download once.
 */
class WebSenseRuntime : SenseRuntime {
    override val available: Boolean = cachesSupported()

    override suspend fun downloaded(): Boolean = call<Boolean> { done -> isCached(SenseRuntime.MODEL_URL) { a -> isCached(SenseRuntime.TOKENIZER_URL) { b -> done(a && b) } } }

    override suspend fun download(progress: (Float) -> Unit) {
        fetchInto(SenseRuntime.TOKENIZER_URL) { progress(it * TOKENIZER_SHARE) }
        fetchInto(SenseRuntime.MODEL_URL) { progress(TOKENIZER_SHARE + it * (1 - TOKENIZER_SHARE)) }
    }

    private suspend fun fetchInto(url: String, progress: (Float) -> Unit) {
        val error = call<String> { done -> cacheUrl(url, { p -> progress(p.toFloat()) }, done) }
        check(error.isEmpty()) { error }
    }

    override suspend fun tokenizerJson(): String = call { done -> cachedText(SenseRuntime.TOKENIZER_URL, done) }

    override suspend fun open(): SentenceEncoder {
        val error = call<String> { done -> openModel(SenseRuntime.MODEL_URL, done) }
        check(error.isEmpty()) { error }
        return SentenceEncoder { ids ->
            val size = call<Int> { done -> runModel(ids.joinToString(","), done) }
            check(size > 0) { "The model could not read this sentence" }
            FloatArray(size) { outputAt(it).toFloat() }
        }
    }

    override suspend fun remove() = call<Boolean> { done -> clearCache(done) }.let {}

    private suspend fun <T> call(block: ((T) -> Unit) -> Unit): T = suspendCancellableCoroutine { cont -> block { if (cont.isActive) cont.resume(it) } }

    private companion object {
        const val TOKENIZER_SHARE = 0.12f
    }
}

private fun cachesSupported(): Boolean = js("('caches' in globalThis) && ('fetch' in globalThis)")

private fun isCached(url: String, done: (Boolean) -> Unit): Unit = js(
    """{ caches.open('hearth-sense').then((c) => c.match(url)).then((r) => done(!!r)).catch(() => done(false)); }""",
)

/** Downloads [url] into the cache, telling how far it is (0–1); done with "" or what went wrong. */
private fun cacheUrl(url: String, progress: (Double) -> Unit, done: (String) -> Unit): Unit = js(
    """{
        (async () => {
            const r = await fetch(url);
            if (!r.ok) throw new Error('HTTP ' + r.status);
            const total = Number(r.headers.get('content-length')) || 0;
            const reader = r.body.getReader();
            const chunks = []; let got = 0;
            for (;;) {
                const { done: end, value } = await reader.read();
                if (end) break;
                chunks.push(value); got += value.length;
                if (total) progress(Math.min(1, got / total));
            }
            const cache = await caches.open('hearth-sense');
            await cache.put(url, new Response(new Blob(chunks), { headers: { 'content-type': r.headers.get('content-type') || 'application/octet-stream' } }));
            progress(1);
        })().then(() => done(''), (e) => done(String(e && e.message || e)));
    }""",
)

private fun cachedText(url: String, done: (String) -> Unit): Unit = js(
    """{ caches.open('hearth-sense').then((c) => c.match(url)).then((r) => r ? r.text() : '').then(done, () => done('')); }""",
)

/** Loads ONNX Runtime Web (once) and opens the cached model; done with "" or what went wrong. */
private fun openModel(url: String, done: (String) -> Unit): Unit = js(
    """{
        const base = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.20.0/dist/';
        const ready = globalThis.ort ? Promise.resolve() : new Promise((ok, fail) => {
            const s = document.createElement('script');
            s.src = base + 'ort.min.js';
            s.onload = ok; s.onerror = () => fail(new Error('ONNX Runtime did not load'));
            document.head.appendChild(s);
        });
        ready.then(async () => {
            ort.env.wasm.wasmPaths = base;
            ort.env.wasm.numThreads = 1;
            const r = await (await caches.open('hearth-sense')).match(url);
            if (!r) throw new Error('The model is not downloaded');
            globalThis.__hearthSense = { session: await ort.InferenceSession.create(new Uint8Array(await r.arrayBuffer()), { executionProviders: ['wasm'] }), out: null };
        }).then(() => done(''), (e) => done(String(e && e.message || e)));
    }""",
)

/** Runs the model on comma-separated token ids; done with how many floats came out (0: it failed). */
private fun runModel(ids: String, done: (Int) -> Unit): Unit = js(
    """{
        const s = globalThis.__hearthSense;
        if (!s) { done(0); return; }
        const values = ids.split(',').map((x) => BigInt(x));
        const shape = [1, values.length];
        const feeds = {
            input_ids: new ort.Tensor('int64', BigInt64Array.from(values), shape),
            attention_mask: new ort.Tensor('int64', new BigInt64Array(values.length).fill(1n), shape),
            token_type_ids: new ort.Tensor('int64', new BigInt64Array(values.length), shape),
        };
        s.session.run(feeds).then((r) => { s.out = r.last_hidden_state.data; done(s.out.length); }, () => done(0));
    }""",
)

private fun outputAt(i: Int): Double = js("globalThis.__hearthSense.out[i]")

private fun clearCache(done: (Boolean) -> Unit): Unit = js(
    """{ globalThis.__hearthSense = null; caches.delete('hearth-sense').then(() => done(true), () => done(false)); }""",
)
