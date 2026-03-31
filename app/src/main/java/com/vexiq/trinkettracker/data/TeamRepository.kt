package com.vexiq.trinkettracker.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.InputStream

class TeamRepository(
    private val teamDao: TeamDao,
    private val context: Context
) {

    companion object {
        private const val TAG = "TeamRepository"
        private const val TEAMS_URL =
            "https://www.robotevents.com/eventEntities/64028/teamsReport"
        // Full Chrome-on-Android UA so the server treats us identically to the browser
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    fun getNotCollectedTeams(): Flow<List<Team>> = teamDao.getNotCollectedTeams()
    fun getCollectedTeams(): Flow<List<Team>> = teamDao.getCollectedTeams()
    fun getAllTeams(): Flow<List<Team>> = teamDao.getAllTeams()

    suspend fun markTeamCollected(teamNumber: String, photoPath: String) {
        teamDao.markCollected(teamNumber, photoPath, System.currentTimeMillis())
    }

    suspend fun retakeTeamPhoto(teamNumber: String, photoPath: String) {
        teamDao.markCollected(teamNumber, photoPath, System.currentTimeMillis())
    }

    suspend fun removeTeams(teamNumbers: List<String>) {
        teamDao.unmarkCollectedBatch(teamNumbers)
    }

    /**
     * Downloads the XLS via Android's DownloadManager — this uses the system
     * network stack (same cookies, certificates and proxy settings as Chrome),
     * which prevents the 403 errors that a plain HTTP client can hit on some
     * corporate / school WiFi networks.
     */
    suspend fun refreshTeams(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Put the file in the app's cache so we can read it back
            val destFile = File(context.cacheDir, "teams_report.xls")
            if (destFile.exists()) destFile.delete()

            val request = DownloadManager.Request(Uri.parse(TEAMS_URL))
                .setTitle("VEX IQ Team List")
                .setDescription("Downloading team report…")
                .addRequestHeader("User-Agent", CHROME_UA)
                .addRequestHeader(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;" +
                            "q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                )
                .addRequestHeader("Accept-Language", "en-US,en;q=0.9")
                .addRequestHeader("Cache-Control", "no-cache")
                .addRequestHeader("Pragma", "no-cache")
                .addRequestHeader("Upgrade-Insecure-Requests", "1")
                .setDestinationUri(Uri.fromFile(destFile))
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )

            val downloadId = dm.enqueue(request)
            Log.d(TAG, "DownloadManager enqueued id=$downloadId")

            // Poll until complete, failed, or timed out (60 s)
            val deadline = System.currentTimeMillis() + 60_000L
            var status = DownloadManager.STATUS_PENDING
            var reason = 0

            while (System.currentTimeMillis() < deadline) {
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    reason = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    )
                    cursor.close()
                } else {
                    cursor?.close()
                }

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> break
                    DownloadManager.STATUS_FAILED -> {
                        dm.remove(downloadId)
                        return@withContext Result.failure(
                            Exception("Download failed (reason=$reason). Check your internet connection and try again.")
                        )
                    }
                }
                delay(400)
            }

            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                dm.remove(downloadId)
                return@withContext Result.failure(Exception("Download timed out after 60 s"))
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext Result.failure(
                    Exception("Downloaded file is empty or missing")
                )
            }

            Log.d(TAG, "File downloaded: ${destFile.length()} bytes")

            // Parse and sync
            val teams = parseXls(destFile.inputStream())
            destFile.delete()

            if (teams.isEmpty()) {
                return@withContext Result.failure(
                    Exception("No teams found in the downloaded file")
                )
            }

            val collectedNumbers = teamDao.getCollectedTeamNumbers().toSet()

            teams.forEach { (number, name) ->
                val existing = teamDao.getTeamByNumber(number)
                if (existing == null) {
                    teamDao.insertTeam(
                        Team(
                            teamNumber = number,
                            teamName = name,
                            isCollected = collectedNumbers.contains(number)
                        )
                    )
                } else if (existing.teamName != name) {
                    teamDao.updateTeam(existing.copy(teamName = name))
                }
            }

            Log.d(TAG, "Synced ${teams.size} teams")
            Result.success(teams.size)

        } catch (e: Exception) {
            Log.e(TAG, "refreshTeams failed", e)
            Result.failure(e)
        }
    }

    /**
     * Parses a legacy .xls file (HSSF format).
     * Row 0 = header ("Team", "Team Name", …); data starts at row 1.
     */
    private fun parseXls(inputStream: InputStream): List<Pair<String, String>> {
        val teams = mutableListOf<Pair<String, String>>()
        return try {
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            Log.d(TAG, "Sheet rows: ${sheet.lastRowNum + 1}")

            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val rawNumber = row.getCell(0)?.toString()?.trim() ?: ""
                val rawName = row.getCell(1)?.toString()?.trim() ?: ""
                if (rawNumber.isEmpty()) continue

                // POI may stringify numeric cells as "12345.0" — strip trailing ".0"
                val teamNumber = if (rawNumber.matches(Regex("\\d+\\.0")))
                    rawNumber.dropLast(2) else rawNumber

                teams.add(teamNumber to rawName)
            }

            workbook.close()
            Log.d(TAG, "Parsed ${teams.size} teams")
            teams
        } catch (e: Exception) {
            Log.e(TAG, "XLS parse error", e)
            teams
        }
    }
}
