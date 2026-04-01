package com.vexiq.trinkettracker.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
        const val TEAMS_URL =
            "https://www.robotevents.com/eventEntities/64028/teamsReport"
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

    suspend fun refreshTeams(): RefreshResult = withContext(Dispatchers.IO) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val destDir = context.getExternalFilesDir(null)
                ?: return@withContext RefreshResult.Failure("External storage unavailable", is403 = false)
            val destFile = File(destDir, "teams_report.xls")
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

            val deadline = System.currentTimeMillis() + 60_000L
            var status = DownloadManager.STATUS_PENDING
            var reason = 0

            while (System.currentTimeMillis() < deadline) {
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    cursor.close()
                } else {
                    cursor?.close()
                }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> break
                    DownloadManager.STATUS_FAILED -> {
                        dm.remove(downloadId)
                        val is403 = reason == 403
                        return@withContext RefreshResult.Failure(
                            if (is403) "Access denied (HTTP 403). Please import the file manually."
                            else "Download failed (code $reason). Try again or import manually.",
                            is403 = is403
                        )
                    }
                }
                delay(400)
            }

            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                dm.remove(downloadId)
                return@withContext RefreshResult.Failure("Download timed out. Try importing manually.", is403 = false)
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext RefreshResult.Failure("Downloaded file is empty.", is403 = false)
            }

            val count = syncFromStream(destFile.inputStream())
            destFile.delete()

            if (count == 0) RefreshResult.Failure("No teams found in file.", is403 = false)
            else RefreshResult.Success(count)

        } catch (e: Exception) {
            Log.e(TAG, "refreshTeams failed", e)
            RefreshResult.Failure(e.message ?: "Unknown error", is403 = false)
        }
    }

    suspend fun importXlsFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open the selected file."))
            val count = syncFromStream(stream)
            if (count == 0) Result.failure(Exception("No teams found in the selected file. Make sure you picked the correct XLS."))
            else Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "importXlsFromUri failed", e)
            Result.failure(Exception("Failed to read file: ${e.message}"))
        }
    }

    private suspend fun syncFromStream(inputStream: InputStream): Int {
        val teams = parseXls(inputStream)
        if (teams.isEmpty()) return 0

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
        return teams.size
    }

    private fun parseXls(inputStream: InputStream): List<Pair<String, String>> {
        val teams = mutableListOf<Pair<String, String>>()
        return try {
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            Log.d(TAG, "Sheet rows: ${sheet.lastRowNum + 1}")

            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val rawNumber = row.getCell(0)?.toString()?.trim() ?: ""
                val rawName   = row.getCell(1)?.toString()?.trim() ?: ""
                if (rawNumber.isEmpty()) continue
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

sealed class RefreshResult {
    data class Success(val count: Int) : RefreshResult()
    data class Failure(val message: String, val is403: Boolean) : RefreshResult()
}
