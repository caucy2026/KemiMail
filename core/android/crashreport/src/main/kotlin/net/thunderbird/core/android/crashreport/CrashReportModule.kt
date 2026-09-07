package net.thunderbird.core.android.crashreport

import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val CRASH_REPORT_DIRECTORY = "crash-diagnostics"
private const val CRASH_REPORT_FILE = "latest-v1.report"

val crashReportModule = module {
    single<CrashReportEnvironmentProvider> {
        AndroidCrashReportEnvironmentProvider(context = androidContext())
    }
    single<CrashReportFile> {
        AndroidAtomicCrashReportFile(
            file = File(androidContext().noBackupFilesDir, "$CRASH_REPORT_DIRECTORY/$CRASH_REPORT_FILE"),
        )
    }
    single { CrashReportFactory(environmentProvider = get()) }
    single {
        LocalCrashReportRepository(
            reportFile = get(),
            reportFactory = get(),
        )
    }
    single<CrashReportRecorder> { get<LocalCrashReportRepository>() }
    single<CrashReportRepository> { get<LocalCrashReportRepository>() }
}
