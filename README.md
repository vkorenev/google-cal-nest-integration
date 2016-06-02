# Nest Integration with Google Calendar

[![Build Status](https://travis-ci.org/vkorenev/google-cal-nest-integration.svg?branch=master)](https://travis-ci.org/vkorenev/google-cal-nest-integration)

This application watches Google Calendars for events which are going to be held at home.
Then it reports ETA based on assumption that the user returns home before such an event.

## Requirements

[Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

[SBT 0.13.11](http://www.scala-sbt.org/download.html)

## Running

Before running the application you must configure OAuth 2 credentials for accessing Nest and Google Calendar.
Copy [`/conf/auth.conf.template`](conf/auth.conf.template) to `/conf/auth.conf` and follow the instructions in this file.

Then you can start the application on localhost by executing `sbt run` in the root directory.
