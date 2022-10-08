package org.example.discord.bot

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.example.discord.bot.cli.AutoExpireRolesCli
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 1. Create a application https://discord.com/developers/applications
 *
 * 2. Add A Bot
 *
 *    Be sure to give the bot in your application the following rights (under Privileged Gateway Intents):
 *    SERVER MEMBERS INTENT
 *    MESSAGE CONTENT INTENT
 *
 * 3. Then get the client id (replace the ...) from the OAuth2 menu and authorize the bot for the server you
 *    want (permissions are "Send Message" and "Manage Roles")
 *    https://discord.com/oauth2/authorize?client_id=...&scope=bot&permissions=268437504
 *
 * 4. Change the runtime Paramters (the bot checks every 20 seconds) and the DEFAULT_DURATIONS
 *    especially the role names you want to have managed
 *
 * 5. Run this as java application with the bot token as parameter
 *
 * The bot reacts on the command !expires in a text channel
 *
 * This prototype has only RAM storage that means on restart the expries info is lost and
 * starts with the default values per role again.
 */
class AutoExpireRoles {

    private static Logger LOG = LoggerFactory.getLogger(AutoExpireRoles)

    /**
     * A set of managed role names (currently identified only by name see method getRoleKey)
     */
    private static final Map<String, List<Object>> DEFAULT_DURATIONS = new ConcurrentHashMap<>()

    private static final Map<String, Instant> USER_EXPIRATIONS = new ConcurrentHashMap<>()

    static void main(String[] args) throws InterruptedException {
        def cli = AutoExpireRolesCli.parseArgs(args)
        if (cli == null) {
            System.exit(0)
        }

        def (initDelay,period,timeUnit) = AutoExpireRolesCli.readConfig(cli.d?cli.d:null, DEFAULT_DURATIONS)
        if (initDelay == null || period == null || timeUnit == null) {
            System.exit(-1)
        }

        final JDA jda = JDABuilder.createDefault(cli.token)
                .addEventListeners(new CommandListener())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, // special bot rights "SERVER MEMBERS INTENT"
                        GatewayIntent.MESSAGE_CONTENT) // and "MESSAGE CONTENT INTENT"
                .build()

        jda.awaitReady()

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jda.shutdown()
            LOG.info('shutdown')
        }))

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                expireRoles(jda)
            }, initDelay, period, timeUnit)
    }

    private static void expireRoles(JDA jda) {
        try {
            LOG.info('started epxireRoles()')
            Instant now = Instant.now()

            List<Guild> guilds =
                    jda.getGuilds()

            def handledMemberEntries = []
            for (Guild g : guilds) {
                LOG.info("load all members of guild $g.name")
                g.loadMembers().get() // load all members at one instead of using findMembersByRole
                LOG.info("members loaded")

                for (Role r : g.getRoles()) {
                    if (getManagedRoles().contains(getRoleKey(r))) {
                        LOG.info("found managed role $r.name in guild $g.name")
                        handledMemberEntries.addAll(checkAndExpireMemberForRole(g, r, now))
                    }
                }
            }

            USER_EXPIRATIONS.each {
                if (!handledMemberEntries.contains(it.key)) {
                    removeExpirationEntry(jda, it.key, it.value)
                }
            }
            LOG.info('ended epxireRoles()')
            LOG.debug('----------------------------')
        } catch (RuntimeException e) {
            e.printStackTrace()
        }
    }

    private static void removeExpirationEntry(JDA jda, String guildUserKey, Instant savedExpiration) {
        def (roleById, memberById) = reverseGuildUserKey(jda, guildUserKey)
        LOG.info("removed role ${roleById?.name} cause the member ${memberById?.effectiveName} " +
                "has lost the role was tue $savedExpiration)")
        removeFromExpireMap(guildUserKey)
    }

    private static Instant removeFromExpireMap(String guildUserKey) {
        USER_EXPIRATIONS.remove(guildUserKey)
    }

    private static List<String> checkAndExpireMemberForRole(Guild g, Role r, Instant now) {
        def handledMembers = []
        List<Member> members = g.getMembersWithRoles(r)
        if (members.isEmpty()) {
            LOG.debug('... no users found')
        }
        for (Member member : members) {
            String guildUserKey = addDefaultExpiration(g, r, member)
            handledMembers << guildUserKey

            Instant expirationInstant = USER_EXPIRATIONS.get(guildUserKey)

            if (now.isAfter(expirationInstant)) {
                LOG.info("Queued expire of role ($r.name) " +
                        "from user ($member.effectiveName) in guild ($r.name)")
                USER_EXPIRATIONS.remove(guildUserKey)
                g.removeRoleFromMember(member, r).queue()
            } else {
                LOG.debug("$now < $expirationInstant not expired of role ($r.name) " +
                        "from user ($member.effectiveName) in guild ($g.name)")
            }
        }
        return handledMembers
    }

    private static String addDefaultExpiration(Guild g, Role r, Member member) {
        String guildUserKey = getGuildUserKey(g, r, member)

        if (!USER_EXPIRATIONS.contains(guildUserKey)) {
            LOG.debug("$g.name member $member.effectiveName got managed role $r.name")
            USER_EXPIRATIONS.putIfAbsent(guildUserKey,
                    getDefaultInstant(DEFAULT_DURATIONS.get(getRoleKey(r))))
        }
        guildUserKey
    }

    private static String getRoleKey(Role r) {
        return r.getName()
    }

    private static Set<String> getManagedRoles() {
        return DEFAULT_DURATIONS.keySet()
    }

    private static Instant getDefaultInstant(List<Object> params) {
        return Instant.now().plus((Integer) params[0], ChronoUnit."${params[1]}")
    }

    private static String getGuildUserKey(Guild g, Role r, Member member) {
        return g.getId() + '::' + member.getId() + '::' + r.getId()
    }

    private static def reverseGuildUserKey(JDA jda, String key) {
        def (guildId, memberId, roleId) = key.split('::')

        def guildById = jda.getGuildById(guildId)
        def roleById = guildById.getRoleById(roleId)
        def memberById = guildById.getMemberById(memberId)

        return [roleById, memberById]
    }

    private static class CommandListener extends ListenerAdapter {
        @Override
        void onMessageReceived(MessageReceivedEvent event) {
            String message = event.getMessage().getContentDisplay()
            if (event.getMember() != null && message.trim().equalsIgnoreCase('!expires')) {
                if (event.isFromType(ChannelType.TEXT)) {
                    replyExpireStats(event.getChannel(), event.getGuild(), event.getMember())
                }
            }
        }

        @Override
        void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
            def rolesAdded = event.getRoles()
            def member = event.getMember()
            def guild = event.getGuild()
            for (Role r in rolesAdded) {
                if (managedRoles.contains(getRoleKey(r))) {
                    addDefaultExpiration(guild, r, member)
                }
            }
        }

        @Override
        void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
            event.getRoles()
            def rolesAdded = event.getRoles()
            def member = event.getMember()
            def guild = event.getGuild()
            for (Role r in rolesAdded) {
                if (managedRoles.contains(getRoleKey(r))) {
                    def guildUserKey = getGuildUserKey(guild, r, member)
                    if (USER_EXPIRATIONS.containsKey(guildUserKey)) {
                        LOG.info("removed managed role ${r.name} cause the member ${member.effectiveName} " +
                                "has lost the role was tue ${USER_EXPIRATIONS.get(guildUserKey)}")
                        removeFromExpireMap(guildUserKey)
                    }
                }
            }

        }

        private static void replyExpireStats(MessageChannelUnion channel, Guild guild, Member member) {
            List<Role> memberRoles = member.getRoles()
            EmbedBuilder eb = new EmbedBuilder()
            eb.setTitle("${member.getEffectiveName()} roles expire in:", null)
            boolean foundRole = false
            for (Role r : memberRoles) {
                if (getManagedRoles().contains(getRoleKey(r))) {
                    if (!foundRole) {
                        foundRole = true
                    }
                    Instant expires = USER_EXPIRATIONS.getOrDefault(getGuildUserKey(guild, r, member),
                            getDefaultInstant(DEFAULT_DURATIONS.get(getRoleKey(r))))
                    Duration duration = Duration.between(Instant.now(), expires)
                    if (duration.isNegative()) {
                        String time = formatExpireString(duration.abs())
                        eb.addField(r.name, "is expired since $time", false)
                    } else {
                        String time = formatExpireString(duration)
                        eb.addField(r.name, "expires in $time", false)
                    }
                }
            }
            if (!foundRole) {
                eb.addField("no role found to expire", "", false)
            }

            def embed = eb.build()
            def messageBuilder = new MessageCreateBuilder()
            messageBuilder.addEmbeds(embed)
            channel.sendMessage(messageBuilder.build()).queue()
        }

        private static String formatExpireString(Duration duration) {
            def days = duration.toDaysPart()
            def hours = duration.toHoursPart()
            def mins = duration.toMinutesPart()
            def secs = duration.toSecondsPart()
            def time = (days ? "$days days, " : '') +
                       (hours ? "$hours hours, " : '') +
                       (mins ? "$mins minutes and " : '') +
                    "$secs seconds"
            time
        }
    }
}