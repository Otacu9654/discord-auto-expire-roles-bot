import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
class Main {

    private static Logger LOG = LoggerFactory.getLogger(Main)

    public static final int INITIAL_DELAY = 0 // dependend on the UNIT in this case 0 SECONDS
    public static final int PERIOD = 20 // dependend on the UNIT in this case 10 SECONDS
    public static final TimeUnit UNIT = TimeUnit.SECONDS

    private static final ChronoUnit DEFAULT_UNIT = ChronoUnit.MINUTES

    /**
     * A set of managed role names (currently identified only by name see method getRoleKey)
     */
    private static final Map<String, Integer> DEFAULT_DURATIONS

    static {
        DEFAULT_DURATIONS = new ConcurrentHashMap<>()
        DEFAULT_DURATIONS.put("TestRole1", 1) // TestRole1 expires in 1 MINUTE from now
        DEFAULT_DURATIONS.put("TestRole2", 5) // TestRole2 expires in 5 MINUTES from now
    }

    private static final Map<String, Instant> USER_EXPIRATIONS = new ConcurrentHashMap<>()

    static void main(String[] args) throws InterruptedException {
        final JDA jda = JDABuilder.createDefault(args[0])
                .addEventListeners(new CommandListner())
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
            }, INITIAL_DELAY, PERIOD, UNIT)
    }

    private static void expireRoles(JDA jda) {
        LOG.info('started epxireRoles()')
        Instant now = Instant.now()

        List<Guild> guilds =
                jda.getGuilds()

        def handledMemberEntries = []
        for (Guild g : guilds) {
            for (Role r : g.getRoles()) {
                if (getManagedRoles().contains(getRoleKey(r))) {
                    LOG.info("found managed role $r.name:$r.id in guild $g.name:$g.id")
                    handledMemberEntries.addAll(checkAndExpireMemberForRole(g, r, now))
                }
            }
        }

        USER_EXPIRATIONS.each{
            if (!handledMemberEntries.contains(it.key)) {
                def (roleById, memberById) = reverseGuildUserKey(jda, it.key)
                LOG.info("removed role ${roleById?.name} cause the member ${memberById?.effectiveName} " +
                        "has lost the role was tue $it.value")
                USER_EXPIRATIONS.remove(it.key)
            }
        }
        LOG.info('ended epxireRoles()')
        LOG.debug('----------------------------')
    }

    private static List<String> checkAndExpireMemberForRole(Guild g, Role r, Instant now) {
        def handledMembers = []
        List<Member> members = g.findMembersWithRoles(r).get()
        if (members.isEmpty()) {
            LOG.debug('... no users found')
        }
        for (Member member : members) {
            String guildUserKey = getGuildUserKey(g, r, member)
            handledMembers << guildUserKey

            USER_EXPIRATIONS.putIfAbsent(guildUserKey,
                    getDefaultInstant(DEFAULT_DURATIONS.get(getRoleKey(r))))
            Instant expirationInstant = USER_EXPIRATIONS.get(guildUserKey)

            if (now.isAfter(expirationInstant)) {
                LOG.debug("Queued expire of role ($r.name) " +
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

    private static String getRoleKey(Role r) {
        return r.getName()
    }

    private static Set<String> getManagedRoles() {
        return DEFAULT_DURATIONS.keySet()
    }

    private static Instant getDefaultInstant(Integer integer) {
        return Instant.now().plus(integer, DEFAULT_UNIT)
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

    private static class CommandListner extends ListenerAdapter {
        @Override
        void onMessageReceived(MessageReceivedEvent event) {
            String message = event.getMessage().getContentDisplay()
            if (event.getMember() != null && message.trim().equalsIgnoreCase('!expires')) {
                if (event.isFromType(ChannelType.TEXT)) {
                    replyExpireStats(event.getChannel(), event.getGuild(), event.getMember())
                }
            }
        }

        private static void replyExpireStats(MessageChannelUnion channel, Guild guild, Member member) {
            List<Role> memberRoles = member.getRoles()
            StringBuilder sb = new StringBuilder()
            for (Role r : memberRoles) {
                if (getManagedRoles().contains(getRoleKey(r))) {
                    Instant expires = USER_EXPIRATIONS.getOrDefault(getGuildUserKey(guild, r, member),
                            getDefaultInstant(DEFAULT_DURATIONS.get(getRoleKey(r))))
                    Duration duration = Duration.between(Instant.now(), expires)
                    if (sb.length() == 0) {
                        sb.append(member.getEffectiveName())
                        sb.append('\n')
                    }
                    def days = duration.toDaysPart()
                    def hours = duration.toHoursPart()
                    def mins = duration.toMinutesPart()
                    def secs = duration.toSecondsPart()
                    def time = days?"$days d ":'' + hours?"$hours ":'' + mins?"$mins ":'' + "$secs"

                    if (duration.isNegative()) {
                        sb.append("role $r.name is expired since $time\n")
                    } else {
                        sb.append("role $r.name expires in $time\n")
                    }
                }
            }
            if (sb.length() == 0) {
                channel.sendMessage("no roles found to expire $member.effectiveName").queue()
            } else {
                channel.sendMessage(sb.toString()).queue()
            }
        }
    }
}