@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** The browser's screen wake lock, taken again when the page is shown again, let go when the work is done. */
@Composable
actual fun KeepScreenOn(on: Boolean) {
    DisposableEffect(on) {
        if (on) holdAwake()
        onDispose { if (on) releaseAwake() }
    }
}

private fun holdAwake(): Unit = js(
    """{
        const s = globalThis.__hearthAwake = globalThis.__hearthAwake || {};
        s.on = true;
        s.take = s.take || (() => {
            if (s.on && !s.lock && navigator.wakeLock && document.visibilityState === 'visible') {
                navigator.wakeLock.request('screen').then((l) => {
                    if (s.on) { s.lock = l; l.addEventListener('release', () => { if (s.lock === l) s.lock = null; }); } else l.release();
                }).catch(() => {});
            }
        });
        document.addEventListener('visibilitychange', s.take);
        s.take();
    }"""
)

private fun releaseAwake(): Unit = js(
    """{
        const s = globalThis.__hearthAwake;
        if (!s) return;
        s.on = false;
        document.removeEventListener('visibilitychange', s.take);
        if (s.lock) { s.lock.release().catch(() => {}); s.lock = null; }
    }"""
)
