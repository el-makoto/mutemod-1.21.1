package com.mutemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public class DiscordMute implements ModInitializer {
	public static final String MOD_ID = "discord_mute";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static JDA jda;
	private Path configPath;
	private String botToken;

	@Override
	public void onInitialize() {
		configPath = FabricLoader.getInstance().getConfigDir().resolve("discordmute.properties");
		loadToken(); // Cargar token al iniciar

		if (botToken != null && !botToken.isEmpty()) {
			startBot(botToken, null);
		}

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			// COMANDO: /setmutetoken <token>
			dispatcher.register(CommandManager.literal("setmutetoken")
					.requires(source -> source.hasPermissionLevel(4))
					.then(CommandManager.argument("token", StringArgumentType.string())
							.executes(context -> {
								//coso de mierda te odio
								String newToken = StringArgumentType.getString(context, "token");
								saveToken(newToken);
								context.getSource().sendFeedback(() -> Text.literal("§aToken guardado. Reiniciando bot..."), true);
								startBot(newToken, context.getSource()); // Cambiamos esto para pasar el source
								return 1;
							})));

			// COMANDO: /muteall <type> <nombre/id>
			dispatcher.register(CommandManager.literal("muteall")
					.requires(source -> source.hasPermissionLevel(4))
					.then(CommandManager.literal("channel")
							.then(CommandManager.argument("id", StringArgumentType.string())
									.executes(context -> {
										String id = StringArgumentType.getString(context, "id");
										return toggleMute(context.getSource(), "channel", id, true);
									})))
					.then(CommandManager.literal("player")
							.then(CommandManager.argument("name", StringArgumentType.string())
									.executes(context -> {
										String name = StringArgumentType.getString(context, "name");
										return toggleMute(context.getSource(), "player", name, true);
									}))));

			// COMANDO: /unmuteall (Misma lógica pero false)
			dispatcher.register(CommandManager.literal("unmuteall")
					.requires(source -> source.hasPermissionLevel(4))
					.then(CommandManager.literal("channel")
							.then(CommandManager.argument("id", StringArgumentType.string())
									.executes(context -> toggleMute(context.getSource(), "channel", StringArgumentType.getString(context, "id"), false))))
					.then(CommandManager.literal("player")
							.then(CommandManager.argument("name", StringArgumentType.string())
									.executes(context -> toggleMute(context.getSource(), "player", StringArgumentType.getString(context, "name"), false)))));
		});
	}

	private int toggleMute(ServerCommandSource source, String type, String target, boolean mute) {
		if (jda == null) {
			source.sendError(Text.literal("El bot no está conectado."));
			return 0;
		}

		if (type.equals("channel")) {
			VoiceChannel channel = jda.getVoiceChannelById(target);
			if (channel != null) {
				channel.getMembers().forEach(m -> m.mute(mute).queue());
				source.sendFeedback(() -> Text.literal("§6Canal " + (mute ? "muteado" : "desmuteado")), true);
			} else {
				source.sendError(Text.literal("ID de canal inválido."));
			}
		} else {
			// Mute por nombre de usuario el @ (case insensitive)
			jda.getGuilds().forEach(guild -> {
				for (Member m : guild.getMembers()) {
					if (m.getUser().getName().equalsIgnoreCase(target)) {
						m.mute(mute).queue();
						source.sendFeedback(() -> Text.literal("§bUsuario " + target + (mute ? " muteado" : " desmuteado")), true);
					}
				}
			});
		}
		return 1;
	}

	private void startBot(String token, ServerCommandSource source) {
		if (jda != null) jda.shutdownNow();
		new Thread(() -> {
			try {
				// Agregamos los Intents para que JDA 6 no de problemas con los miembros
				jda = JDABuilder.createDefault(token)
						.setChunkingFilter(net.dv8tion.jda.api.utils.ChunkingFilter.NONE) // No cargues todos los usuarios de una
						.setMemberCachePolicy(net.dv8tion.jda.api.utils.MemberCachePolicy.VOICE) // Solo cacheá a los que están en voz
						.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES)
						.build()
						.awaitReady();
				LOGGER.info("Bot conectado con éxito.");

				// Si el source no es nulo (porque se activó por comando), avisamos al jugador
				if (source != null) {
					source.sendFeedback(() -> Text.literal("§a§l[DiscordMute] §f¡Bot conectado con éxito!"), true);
				}
			} catch (Exception e) {
				LOGGER.error("Error al conectar: " + e.getMessage());
				if (source != null) {
					source.sendError(Text.literal("§cError al conectar el bot: " + e.getMessage()));
				}
			}
		}, "Discord-Init-Thread").start();
	}

	private void saveToken(String token) {
		try (OutputStream output = new FileOutputStream(configPath.toFile())) {
			Properties prop = new Properties();
			prop.setProperty("token", token);
			prop.store(output, null);
			this.botToken = token;
		} catch (IOException io) {
			LOGGER.error("No se pudo guardar el token.");
		}
	}

	private void loadToken() {
		if (configPath.toFile().exists()) {
			try (InputStream input = new FileInputStream(configPath.toFile())) {
				Properties prop = new Properties();
				prop.load(input);
				this.botToken = prop.getProperty("token");
			} catch (IOException ex) {
				LOGGER.error("Error al cargar el token.");
			}
		}
	}
}