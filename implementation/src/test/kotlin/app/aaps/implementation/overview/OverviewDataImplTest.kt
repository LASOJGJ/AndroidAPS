package app.aaps.implementation.overview

import app.aaps.core.data.db.GlucoseUnit
import app.aaps.core.data.db.SourceSensor
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.impl.AppRepository
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class OverviewDataImplTest : TestBaseWithProfile() {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var sut: OverviewDataImpl
    private val time = 1000000L

    private val glucoseValue =
        GlucoseValue(raw = 200.0, noise = 0.0, value = 200.0, timestamp = time, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT)

    @BeforeEach
    fun setup() {
        Mockito.`when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
        sut = OverviewDataImpl(aapsLogger, rh, dateUtil, sp, activePlugin, defaultValueHelper, profileFunction, repository, decimalFormatter) { iobCobCalculator }
        Mockito.`when`(defaultValueHelper.determineLowLine()).thenReturn(80.0)
        Mockito.`when`(defaultValueHelper.determineHighLine()).thenReturn(180.0)
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun lastBg() {
        val bucketedData: MutableList<InMemoryGlucoseValue> = mutableListOf()
        bucketedData.add(InMemoryGlucoseValue(time, 70.0, sourceSensor = SourceSensor.UNKNOWN))
        // no data
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Absent()))
        assertThat(sut.lastBg()).isNull()
        assertThat(sut.isLow()).isFalse()
        assertThat(sut.isHigh()).isFalse()

        // no bucketed but in db
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Existing(glucoseValue)))
        assertThat(sut.lastBg()?.value).isEqualTo(200.0)
        assertThat(sut.isLow()).isFalse()
        assertThat(sut.isHigh()).isTrue()

        // in bucketed
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(bucketedData)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Existing(glucoseValue)))
        assertThat(sut.lastBg()?.value).isEqualTo(70.0)
        assertThat(sut.isLow()).isTrue()
        assertThat(sut.isHigh()).isFalse()
    }

    @Test
    fun isActualBg() {
        // no bucketed but in db
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Existing(glucoseValue)))
        Mockito.`when`(dateUtil.now()).thenReturn(time + T.mins(1).msecs())
        assertThat(sut.isActualBg()).isTrue()
        Mockito.`when`(dateUtil.now()).thenReturn(time + T.mins(9).msecs() + 1)
        assertThat(sut.isActualBg()).isFalse()

        // no data
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Absent()))
        assertThat(sut.isActualBg()).isFalse()
    }
}
