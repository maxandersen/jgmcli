///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS io.quarkus.platform:quarkus-bom:3.34.1@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-jackson

//DEPS com.google.api-client:google-api-client:2.4.0
//DEPS com.google.apis:google-api-services-gmail:v1-rev20260112-2.0.0
//DEPS com.google.oauth-client:google-oauth-client-jetty:1.35.0

//SOURCES dk/xam/jgmcli/cli/AccountsCommand.java
//SOURCES dk/xam/jgmcli/cli/DraftsCommand.java
//SOURCES dk/xam/jgmcli/cli/GmcliCommand.java
//SOURCES dk/xam/jgmcli/cli/LabelsCommand.java
//SOURCES dk/xam/jgmcli/cli/OutputFormatter.java
//SOURCES dk/xam/jgmcli/cli/SearchCommand.java
//SOURCES dk/xam/jgmcli/cli/SendCommand.java
//SOURCES dk/xam/jgmcli/cli/ThreadCommand.java
//SOURCES dk/xam/jgmcli/cli/UrlCommand.java
//SOURCES dk/xam/jgmcli/exception/AccountNotFoundException.java
//SOURCES dk/xam/jgmcli/exception/AuthorizationException.java
//SOURCES dk/xam/jgmcli/exception/GmcliException.java
//SOURCES dk/xam/jgmcli/model/Credentials.java
//SOURCES dk/xam/jgmcli/model/DownloadedAttachment.java
//SOURCES dk/xam/jgmcli/model/GmailAccount.java
//SOURCES dk/xam/jgmcli/model/LabelInfo.java
//SOURCES dk/xam/jgmcli/model/LabelOperationResult.java
//SOURCES dk/xam/jgmcli/model/OAuth2Credentials.java
//SOURCES dk/xam/jgmcli/model/ThreadSearchResult.java
//SOURCES dk/xam/jgmcli/oauth/GmailOAuthFlow.java
//SOURCES dk/xam/jgmcli/service/GmailService.java
//SOURCES dk/xam/jgmcli/storage/AccountStorage.java

//FILES application.properties=../resources/application.properties

// JBang bootstrap for jgmcli - Quarkus Picocli handles entry via @TopCommand
// 
// Usage:
//   jbang main.java --help
//   jbang https://github.com/maxandersen/jgmcli/blob/main/src/main/java/main.java --help
