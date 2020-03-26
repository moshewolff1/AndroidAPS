package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.CgmSourceTransaction
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.BundleLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.GlucoseValueUploader
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.toTrendArrow
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlimpPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val broadcastToXDrip: XDripBroadcast,
    private val uploadToNS: GlucoseValueUploader
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.Glimp)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_glimp),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private val disposable = CompositeDisposable()

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun advancedFilteringSupported(): Boolean {
        return false
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.BGSOURCE, "Received Glimp Data: ${BundleLogger.log(bundle)}")
        val bgReading = BgReading()
        bgReading.value = bundle.getDouble("mySGV")
        bgReading.direction = bundle.getString("myTrend")
        bgReading.date = bundle.getLong("myTimestamp")
        bgReading.raw = 0.0
        MainApp.getDbHelper().createIfNotExists(bgReading, "GLIMP")
        val glucoseValue = CgmSourceTransaction.TransactionGlucoseValue(
            timestamp = bundle.getLong("myTimestamp"),
            value = bundle.getDouble("mySGV"),
            raw = null,
            noise = null,
            trendArrow = bundle.getString("myTrend")!!.toTrendArrow(),
            sourceSensor = GlucoseValue.SourceSensor.GLIMP
        )
        disposable += repository.runTransactionForResult(CgmSourceTransaction(listOf(glucoseValue), emptyList(), null)).subscribe({
            it.forEach {
                broadcastToXDrip(it)
                uploadToNS(it, "AndroidAPS-Glimp")
            }
        }, {
            aapsLogger.error(LTag.BGSOURCE, "Error while saving values from Tomato App", it)
        })
    }
}