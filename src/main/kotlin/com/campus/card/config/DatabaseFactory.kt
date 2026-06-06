package com.campus.card.config

import com.campus.card.model.*
import com.typesafe.config.ConfigFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

object DatabaseFactory {

    fun init() {
        val config = ConfigFactory.load()
        val url = config.getString("database.url")
        val driver = config.getString("database.driver")
        val user = config.getString("database.user")
        val password = config.getString("database.password")

        logger.info { "Connecting to database: $url with driver: $driver" }

        Database.connect(url, driver = driver, user = user, password = password)

        transaction {
            SchemaUtils.create(
                Cards,
                LostReports,
                Reissues,
                CardBatches,
                Consumptions,
                Disputes,
                CardOperations
            )
            logger.info { "Database schema initialized successfully" }
        }
    }
}
