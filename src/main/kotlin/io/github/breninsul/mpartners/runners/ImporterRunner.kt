/*
 * MIT License
 *
 * Copyright (c) 2024 BreninSul
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.breninsul.mpartners.runners

import com.fasterxml.jackson.databind.JsonNode
import io.github.breninsul.configurabletransactiontemplatestarter.enums.TransactionPropagation
import io.github.breninsul.configurabletransactiontemplatestarter.template.ConfigurableTransactionTemplate
import io.github.breninsul.jdbctemplatepostgresqltypes.type.toPGJsonb
import io.github.breninsul.mpartners.dto.MicrosoftPartners
import io.github.breninsul.namedlimitedvirtualthreadexecutor.service.VirtualTreadExecutor
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.sql.Types
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Callable
import kotlin.system.exitProcess

/**
 * This class is responsible for running the importer process.
 *
 * @property restClient The REST client used for making HTTP requests.
 * @property jdbcClient The JDBC client used for interacting with the database.
 * @property transactionTemplate The transaction template for managing transactions.
 */
@Component
open class ImporterRunner(
    val restClient: RestClient,
    val jdbcClient: JdbcClient,
    val transactionTemplate: ConfigurableTransactionTemplate,
    @Value("\${microsoft-api.sort:0}") val sort: Int,
    @Value("\${microsoft-api.max-results:100}") val maxResults: Int,
    @Value("\${microsoft-api.page-size:20}") val pageSize: Int,
    @Value("\${microsoft-api.additional-filter:services=Integration;}") val additionalFilter: String,
    @Value("\${microsoft-api.api-link:https://main.prod.marketplacepartnerdirectory.azure.com/api/partners?filter=sort=@sort@;pageSize=@pageSize@;pageOffset=@offset@;country=@country@;onlyThisCountry=true;@additionalFilter@;}") val apiLink: String,
) : CommandLineRunner, Logging {
    /**
     * Executes the main logic of the ImporterRunner.
     *
     * @param args The command line arguments.
     */
    override fun run(vararg args: String?) {
        val timeStart = LocalDateTime.now()
        val link = apiLink.replace("@additionalFilter@", additionalFilter)
        logger.warn("Start processing. Sort=$sort,maxResults=$maxResults,pageSize=$pageSize,link=$link")

        val countries = mutableSetOf(*Locale.getISOCountries())
        val inserted =
            countries.sumOf { country ->
                getForCountry(country, sort, maxResults, pageSize, link)
            }
        val timeEnd = LocalDateTime.now()
        val took = Duration.between(timeStart, timeEnd)
        logger.warn("Done processing $inserted Sort=$sort,maxResults=$maxResults,pageSize=$pageSize,link=$link. Took ${took.seconds} seconds")
        exitProcess(0)
    }

    /**
     * Retrieves data for a specific country and calculates the total count.
     *
     * @param country The ISO-3166 country code.
     * @param sort The sort parameter.
     * @param maxResults The maximum number of results.
     * @param pageSize The number of results per page.
     * @param link The link for retrieving data.
     * @return The total count of processed data.
     */
    private fun getForCountry(
        country: String,
        sort: Int,
        maxResults: Int,
        pageSize: Int,
        link: String,
    ): Long {
        var totalCount = 0L
        try {
            (0..(maxResults / pageSize)).forEach { page ->
                val processedOnPage = getForPage(country, page, sort, pageSize, link)
                if (processedOnPage < 0) {
                    return totalCount
                } else {
                    totalCount += processedOnPage
                }
            }
        } finally {
            logger.info("$country got new $totalCount")
        }
        return totalCount
    }

    /**
     * Retrieves data for a specific page, processes it, and saves it to the database.
     *
     * @param country The ISO-3166 country code.
     * @param page The page number.
     * @param sort The sort parameter.
     * @param pageSize The number of results per page.
     * @param link The link for retrieving data.
     * @return The total count of processed data.
     */
    private fun getForPage(
        country: String,
        page: Int,
        sort: Int,
        pageSize: Int,
        link: String,
    ): Int {
        val uri =
            link
                .replace("@offset@", (page * pageSize).toString())
                .replace("@country@", country)
                .replace("@sort@", sort.toString())
                .replace("@pageSize@", pageSize.toString())
        try {
            val matchingPartners = restClient.get().uri(uri).retrieve().toEntity(MicrosoftPartners::class.java).body!!.matchingPartners!!
            val partners = matchingPartners.items
            logger.debug("$country $page $uri got ${partners.size}")
            if (partners.isEmpty()) {
                return -1
            }
            val time = System.currentTimeMillis()
            val sum =
                partners.map { partner ->
                    executor.submit(Callable { saveToDb(page, country, partner) })
                }.sumOf { (it.get()) ?: 0 }
            logger.debug("Save ${partners.size} took ${System.currentTimeMillis() - time}ms")
            return sum
        } catch (t: Throwable) {
            logger.error("Error on $country $page $uri", t)
            return -1
        }
    }

    /**
     * Saves the partner data to the database.
     *
     * @param page The page number.
     * @param country The ISO-3166 country code.
     * @param partner The partner data.
     * @return The number of rows affected by the insert operation.
     */
    private fun saveToDb(
        page: Int,
        country: String?,
        partner: JsonNode,
    ): Int? {
        return transactionTemplate.execute(readOnly = false, propagation = TransactionPropagation.REQUIRES_NEW) {
            jdbcClient.sql(
                """
                                insert into microsoft.partners(page_number, country, raw) 
                                VALUES (:page,:country,:raw)
                                on conflict (microsoft_id) do nothing 
                            """,
            )
                .param("page", page, Types.INTEGER)
                .param("country", country, Types.VARCHAR)
                .param("raw", partner.toPGJsonb(), Types.OTHER)
                .update()
        }
    }

    /**
     * The Companion class contains static properties and constants related to the ImporterRunner class.
     */
    companion object {
        /**
         * Virtual thread executor.
         */
        protected val executor = VirtualTreadExecutor
    }
}
