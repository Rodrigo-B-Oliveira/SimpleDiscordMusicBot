/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pt.bandeiraoliveira.discord.musicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 *
 * @author Rodrigo
 */
public class MusicPlayerListenerAdapter extends ListenerAdapter {

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public MusicPlayerListenerAdapter() {
        this.musicManagers = new HashMap<>();

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        String[] command = event.getMessage().getContentRaw().split(" ", 2);

        switch (command[0]) {
            case "t!p":
            case "t!play": {
                if (command.length == 2) {
                    loadAndPlay(event.getChannel(), command[1]);
                }
            }
            break;
            case "t!skip":
                skipTrack(event.getChannel());
                break;
            case "t!v":
            case "t!volume": {
                if (command.length == 2) {
                    try {
                        int i = Integer.valueOf(command[1]);
                        GuildMusicManager musicManager = getGuildAudioPlayer(event.getChannel().getGuild());
                        musicManager.player.setVolume(i);
                        event.getChannel().sendMessage("Volume: " + i).queue();
                    } catch (NumberFormatException ex) {
                        event.getChannel().sendMessage("Error Changing Volume, \"" + command[1] + "\" could not be converted to a number").queue();
                    }
                }
            }
            break;
            case "t!q":
            case "t!queue": {
                if (command.length == 1) {
                    GuildMusicManager musicManager = getGuildAudioPlayer(event.getChannel().getGuild());
                    event.getChannel().sendMessage("Queue:\n").queue();
                    musicManager.scheduler.getPlayerQueue().forEach(at -> event.getChannel().sendMessage(at.getInfo().title + "\n").queue());
                }
            }
            break;
            case "t!playing": {
                if (command.length == 1) {
                    GuildMusicManager musicManager = getGuildAudioPlayer(event.getChannel().getGuild());
                    event.getChannel().sendMessage("Playing:\n").queue();
                    event.getChannel().sendMessage(musicManager.player.getPlayingTrack().getInfo().title + "\n").queue();
                }
            }
            break;
            case "t!stop": {
                if (command.length == 1) {
                    GuildMusicManager musicManager = getGuildAudioPlayer(event.getChannel().getGuild());
                    musicManager.scheduler.getPlayerQueue().clear();
                    musicManager.scheduler.nextTrack();
                    event.getChannel().sendMessage("Stopping\n").queue();
                }
            }
            break;
            case "t!s":
            case "t!search": {
                if (command.length == 2) {
                    String[] queryTermArray = new String[command.length - 1];
                    System.arraycopy(command, 1, queryTermArray, 0, command.length - 1);
                    String queryTerm = String.join(" ", queryTermArray);
                    try {
                        String videoId = Search.query(queryTerm);
                        switch (videoId) {
                            case "" ->
                                event.getChannel().sendMessage("No results found\n").queue();
                            default ->
                                loadAndPlay(event.getChannel(), "https://www.youtube.com/watch?v=" + videoId);
                        }
                    } catch (IllegalArgumentException e) {
                        event.getChannel().sendMessage(e.getMessage() + "\n").queue();
                    }

                }
            }
            break;
            case "t!help": {
                if (command.length == 1) {
                    event.getChannel().sendMessage("""
                        Available commands:
                        t!help
                        t!play <track or playlist>
                        t!skip
                        t!volume <0-200>
                        t!queue
                        t!playing
                        t!stop
                        t!search""").queue();
                }
            }
            break;
            default: {
            }
        }
        super.onGuildMessageReceived(event);
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("Adding to queue " + track.getInfo().title).queue();
                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().size() < 6) {
                    playlist.getTracks().stream().forEach(at -> trackLoaded(at));
                } else {
                    channel.sendMessage("Adding to queue " + playlist.getTracks().size() + " Songs").queue();
                    playlist.getTracks().stream().forEach(at -> play(channel.getGuild(), musicManager, at));
                }
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager());

        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("Skipped to next track.").queue();
    }

}
