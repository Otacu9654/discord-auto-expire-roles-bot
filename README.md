# discord-auto-expire-roles-bot

1. Create a application https://discord.com/developers/applications

2. Add A Bot. Be sure to give the bot in your application the following rights (under Privileged Gateway Intents):
   1. SERVER MEMBERS INTENT
   2. MESSAGE CONTENT INTENT

4. Then get the client id (replace the ...) from the OAuth2 menu and authorize the bot for the server you
want (permissions are "Send Message" and "Manage Roles")
https://discord.com/oauth2/authorize?client_id=...&scope=bot&permissions=268437504

5. Change the runtime Paramters (the bot checks every 20 seconds) and the DEFAULT_DURATIONS
especially the role names you want to have managed

6. Run this as java application with the bot token as parameter

The bot reacts on the command !expires in a text channel

This prototype has only RAM storage that means on restart the expries info is lost and
starts with the default values per role again.