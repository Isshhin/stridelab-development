package io.stridelab.privatealpha

import android.content.Context

/** Design preferences only. No source activity, coordinates or account data are persisted. */
internal class SharePresetStore(context:Context, name:String="share-studio-v1") {
    private val prefs=context.applicationContext.getSharedPreferences(name,Context.MODE_PRIVATE)
    fun load():List<SharePreset> = prefs.getString("designs",null)?.let(SharePresetCodec::decode).orEmpty()
    fun save(presets:List<SharePreset>) {
        require(presets.size<=100) { "You can save up to 100 presets" }
        val raw=SharePresetCodec.encode(presets)
        SharePresetCodec.decode(raw)
        check(prefs.edit().putString("designs",raw).commit()) { "Could not save presets. Try again." }
    }
}
