package kz.alnazar.starremote

import android.app.Application
import android.content.Context
import kz.alnazar.starremote.control.Controller
import kz.alnazar.starremote.data.Journal
import kz.alnazar.starremote.data.SettingsRepo

class StarApp : Application() {
    lateinit var settings: SettingsRepo
    lateinit var journal: Journal
    lateinit var controller: Controller

    val version: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepo(this)
        journal = Journal(this)
        controller = Controller(this, settings, journal)
    }

    companion object {
        fun of(ctx: Context) = ctx.applicationContext as StarApp
    }
}
