@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.mcp

import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.Promise

// WebCrypto. Bytes cross into JavaScript as base64 text; it works on https pages and on localhost.
internal actual object SyncCrypto {
    actual fun random(size: Int): ByteArray = Base64.decode(randomJs(size))

    actual suspend fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
        Base64.decode(sealJs(Base64.encode(key), Base64.encode(plain), Base64.encode(aad)).await<JsString>().toString())

    actual suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray =
        Base64.decode(openJs(Base64.encode(key), Base64.encode(sealed), Base64.encode(aad)).await<JsString>().toString())

    actual suspend fun derive(secret: String, salt: String, iterations: Int): ByteArray =
        Base64.decode(deriveJs(secret, salt, iterations).await<JsString>().toString())
}

private fun randomJs(size: Int): String = js(
    """(() => {
        const a = crypto.getRandomValues(new Uint8Array(size));
        let s = '';
        for (let i = 0; i < a.length; i++) s += String.fromCharCode(a[i]);
        return btoa(s);
    })()""",
)

private fun sealJs(k: String, p: String, a: String): Promise<JsString> = js(
    """(async () => {
        const dec = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
        const enc = (b) => { let s = ''; for (let i = 0; i < b.length; i += 0x8000) s += String.fromCharCode.apply(null, b.subarray(i, i + 0x8000)); return btoa(s); };
        const key = await crypto.subtle.importKey('raw', dec(k), 'AES-GCM', false, ['encrypt']);
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const box = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: dec(a) }, key, dec(p)));
        const out = new Uint8Array(12 + box.length);
        out.set(iv, 0);
        out.set(box, 12);
        return enc(out);
    })()""",
)

private fun openJs(k: String, s: String, a: String): Promise<JsString> = js(
    """(async () => {
        const dec = (t) => Uint8Array.from(atob(t), (c) => c.charCodeAt(0));
        const enc = (b) => { let r = ''; for (let i = 0; i < b.length; i += 0x8000) r += String.fromCharCode.apply(null, b.subarray(i, i + 0x8000)); return btoa(r); };
        const key = await crypto.subtle.importKey('raw', dec(k), 'AES-GCM', false, ['decrypt']);
        const sealed = dec(s);
        const plain = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: sealed.subarray(0, 12), additionalData: dec(a) }, key, sealed.subarray(12)));
        return enc(plain);
    })()""",
)

private fun deriveJs(secret: String, salt: String, iterations: Int): Promise<JsString> = js(
    """(async () => {
        const te = new TextEncoder();
        const base = await crypto.subtle.importKey('raw', te.encode(secret), 'PBKDF2', false, ['deriveBits']);
        const bits = new Uint8Array(await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: te.encode(salt), iterations }, base, 256));
        let s = '';
        for (let i = 0; i < bits.length; i++) s += String.fromCharCode(bits[i]);
        return btoa(s);
    })()""",
)
