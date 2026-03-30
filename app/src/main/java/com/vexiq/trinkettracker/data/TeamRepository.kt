package com.vexiq.trinkettracker.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream
import java.util.concurrent.TimeUnit

class TeamRepository(private val teamDao: TeamDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val teamsReportUrl =
        "https://www.robotevents.com/eventEntities/64028/teamsReport"

    fun getNotCollectedTeams(): Flow<List<Team>> = teamDao.getNotCollectedTeams()

    fun getCollectedTeams(): Flow<List<Team>> = teamDao.getCollectedTeams()

    fun getAllTeams(): Flow<List<Team>> = teamDao.getAllTeams()

    suspend fun getTotalCount(): Int = teamDao.getTotalCount()

    suspend fun getCollectedCount(): Int = teamDao.getCollectedCount()

    suspend fun markTeamCollected(teamNumber: String, photoPath: String) {
        teamDao.markCollected(teamNumber, photoPath, System.currentTimeMillis())
    }

    /**
     * Downloads the XLS team report and updates the local database.
     * Already-collected teams remain collected after refresh.
     */
    suspend fun refreshTeams(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(teamsReportUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; TrinketTracker/1.0)")
                .header("Accept", "application/vnd.ms-excel, application/octet-stream, */*")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Download failed: HTTP ${response.code}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                Exception("Empty response body")
            )

            val teams = parseXls(body.byteStream())

            if (teams.isEmpty()) {
                return@withContext Result.failure(
                    Exception("No teams found in the downloaded file")
                )
            }

            // Get already-collected team numbers to preserve their status
            val collectedNumbers = teamDao.getCollectedTeamNumbers().toSet()

            // Insert new teams (IGNORE strategy means existing rows aren't overwritten)
            val teamEntities = teams.map { (number, name) ->
                Team(
                    teamNumber = number,
                    teamName = name,
                    isCollected = collectedNumbers.contains(number)
                )
            }

            // For teams that exist but have a different name, update the name
            teamEntities.forEach { team ->
                val existing = teamDao.getTeamByNumber(team.teamNumber)
                if (existing == null) {
                    teamDao.insertTeam(team)
                } else if (existing.teamName != team.teamName) {
                    // Update name but preserve collection status
                    teamDao.updateTeam(existing.copy(teamName = team.teamName))
                }
                // If team already exists with correct name, do nothing
            }

            Result.success(teams.size)
        } catch (e: Exception) {
            Log.e("TeamRepository", "Error refreshing teams", e)
            Result.failure(e)
        }
    }

    /**
     * Parses an XLS file input stream and returns a list of (teamNumber, teamName) pairs.
     * Skips the header row (row 0).
     */
    private fun parseXls(inputStream: InputStream): List<Pair<String, String>> {
        val teams = mutableListOf<Pair<String, String>>()

        return try {
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            val lastRow = sheet.lastRowNum
            Log.d("XLS", "Total rows in sheet: ${lastRow + 1}")

            // Row 0 is the header; start from row 1
            for (rowIndex in 1..lastRow) {
                val row = sheet.getRow(rowIndex) ?: continue

                val teamNumberCell = row.getCell(0)
                val teamNameCell = row.getCell(1)

                val teamNumber = teamNumberCell?.toString()?.trim() ?: ""
                val teamName = teamNameCell?.toString()?.trim() ?: ""

                if (teamNumber.isNotEmpty()) {
                    // POI sometimes reads numbers as floats (e.g., "12345.0")
                    // Clean up numeric team numbers
                    val cleanNumber = if (teamNumber.endsWith(".0")) {
                        teamNumber.dropLast(2)
                    } else {
                        teamNumber
                    }
                    teams.add(Pair(cleanNumber, teamName))
                }
            }

            workbook.close()
            Log.d("XLS", "Parsed ${teams.size} teams")
            teams
        } catch (e: Exception) {
            Log.e("TeamRepository", "XLS parsing failed", e)
            teams
        }
    }
}
