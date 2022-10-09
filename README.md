# discord-auto-expire-roles-bot

1. Create a application https://discord.com/developers/applications

2. Add A Bot. Be sure to give the bot in your application the following rights (under Privileged Gateway Intents):
   - SERVER MEMBERS INTENT
   - MESSAGE CONTENT INTENT

3. Then get the client id (replace the ...) from the OAuth2 menu and authorize the bot for the server you
want (permissions are "Send Message" and "Manage Roles")
https://discord.com/oauth2/authorize?client_id=...&scope=bot&permissions=268437504

4. Change the runtime Parameters in the config.json File (is created at startup in the config-directory (default working directory) and the defaultDurations
especially the role names you want to have managed

5. Run this as java application with at least java 11 (the first start creates a sample config.json and quits with an error because the bot token is not set by default)

```
Usage: java -jar AutoExpireRoles<version>.jar
  -d, --config-directory=<directory>
         Path to the directory with the config.json
```

The bot reacts on the command !expires in a text channel you whitelisted in the config.json file.

The state of the user role expire dates is saved in the config-directory/expires-state.json with one backup file on shutdown.

Building from source (assuming you have git and maven installed):
```
git clone https://github.com/Otacu9654/discord-auto-expire-roles-bot.git
cd discord-auto-expire-roles-bot/
mvn clean package
```
