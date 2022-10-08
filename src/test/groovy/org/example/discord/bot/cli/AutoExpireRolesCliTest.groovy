package org.example.discord.bot.cli

import spock.lang.Shared
import spock.lang.Specification

import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

class AutoExpireRolesCliTest extends Specification {

    @Shared
    def origSystemErr = System.err

    def cleanup() {
        System.err = origSystemErr
    }

    def "TestConstructorPrivate"() {
        when:
            new AutoExpireRolesCli()
        then:
            def error = thrown(AssertionError)
            assert error.message == 'helper'
    }

    def "ParseArgs"() {
        when:
            def sw = new ByteArrayOutputStream()
            def err = new PrintStream(sw)
            System.err = err
            def cli = AutoExpireRolesCli.parseArgs((String[])args)
        then:
            def message = new String(sw.toByteArray())
            assert message == expecetedMessage
            if (!message && cli) {
                assert cli.t == 'token'
                if (cli.d) {
                    assert cli.'config-directory' == './target'
                }
            }
        where:
            args                                | expecetedMessage
            []                                  | 'error: Missing required option: \'--token=<token>\'\r\n' +
                                                    'Usage: java -jar AutoExpireRoles<version>.jar -t <token>\r\n' +
                                                    '  -d, --config-directory=<directory>\r\n' +
                                                    '                        Path to the directory with the config.json\r\n' +
                                                    '  -t, --token=<token>   Set the bot token to work with\r\n'
            ['token']                           | 'error: Missing required option: \'--token=<token>\'\r\n' +
                                                    'Usage: java -jar AutoExpireRoles<version>.jar -t <token>\r\n' +
                                                    '  -d, --config-directory=<directory>\r\n' +
                                                    '                        Path to the directory with the config.json\r\n' +
                                                    '  -t, --token=<token>   Set the bot token to work with\r\n'
            ['-t', 'token']                     | ''
            ['-h']                              | ''
            ['--help']                          | ''
            ['-t', 'token', '-blubb']           | ''
            ['-d', './target', '-t', 'token']   | ''
            ['-t', 'token', '-d', './target']   | ''
            ['-t', 'token', '-d', './target', '-h']   | ''
            ['--token', 'token', '--config-directory', './target']   | ''
            ['--token', 'token', '--config-directory', './target']   | ''
    }

    def "ReadConfig"() {
        when:
            Map<String, Object> map = [:]
            if (deleteConfig) {
                def f = new File("$input/config.json")
                if (f.exists()) {
                    f.delete()
                }
            } else {
                if (emptyConfig) {
                    FileChannel.open(Paths.get("$input/config.json"), StandardOpenOption.WRITE).truncate(0).close()
                }
            }
            def (initDelayRes, periodRes, timeUnitRes) = AutoExpireRolesCli.readConfig(input, map)
        then:
            assert initDelay == initDelayRes
            assert period == periodRes
            assert timeUnit == timeUnitRes
            assert map == mapResult
        where:
            deleteConfig | emptyConfig |  input       | initDelay | period | timeUnit         | mapResult
            true         | false       |  './target'  | 0         | 5      | TimeUnit.MINUTES | [TestRole1:[1, "MINUTES"], TestRole2:[5, "MINUTES"], TestRole3:[2, "DAYS"], TestRole4:[23446, "SECONDS"]]
            false        | false       |  './target'  | 0         | 5      | TimeUnit.MINUTES | [TestRole1:[1, "MINUTES"], TestRole2:[5, "MINUTES"], TestRole3:[2, "DAYS"], TestRole4:[23446, "SECONDS"]]
            false        | false       |  './xyz'     | null      | null   | null             | [:]
            false        | true        |  './target'  | null      | null   | null             | [:]
    }
}
