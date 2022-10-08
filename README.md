# discord-auto-expire-roles-bot

1. Create a application https://discord.com/developers/applications

2. Add A Bot. Be sure to give the bot in your application the following rights (under Privileged Gateway Intents):
   - SERVER MEMBERS INTENT
   - MESSAGE CONTENT INTENT

3. Then get the client id (replace the ...) from the OAuth2 menu and authorize the bot for the server you
want (permissions are "Send Message" and "Manage Roles")
https://discord.com/oauth2/authorize?client_id=...&scope=bot&permissions=268437504

4. Change the runtime Paramters in the config.json File (is created at startup in the config-directory (default working directory) and the defaultDurations
especially the role names you want to have managed

5. Run this as java application with the bot token as parameter

```
Usage: java -jar AutoExpireRoles<version>.jar -t <token>
  -d, --config-directory=<directory>
                        Path to the directory with the config.json
  -t, --token=<token>   Set the bot token to work with
```

The bot reacts on the command !expires in a text channel

This prototype has only RAM storage that means on restart the expries info is lost and
starts with the default values per role again.