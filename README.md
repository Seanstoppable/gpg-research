# gpg-research

### Description

A small research project on company gpg usage

Takes a list of vendors with company and size, and scrapes gpg servers by domain
Date is saved to a postgres table, for ease of querying

This project depends on:

 * Scala/sbt
 * [Schema Evolution Manager](https://github.com/mbryzek/schema-evolution-manager)

To set up the database, do the following:

 * Create a gpg user
 * Create a gpg_analysis database with this user.
 * Install Schema Evolution Manager
 * Go to the schema directory and run ./dev.rb

To run the script, simply do 'sbt run'

