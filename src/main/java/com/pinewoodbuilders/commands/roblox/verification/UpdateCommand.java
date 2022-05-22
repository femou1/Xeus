package com.pinewoodbuilders.commands.roblox.verification;

import com.pinewoodbuilders.Xeus;
import com.pinewoodbuilders.commands.CommandMessage;
import com.pinewoodbuilders.contracts.commands.Command;
import com.pinewoodbuilders.contracts.permission.GuildPermissionCheckType;
import com.pinewoodbuilders.contracts.verification.VerificationEntity;
import com.pinewoodbuilders.contracts.verification.VerificationResult;
import com.pinewoodbuilders.database.transformers.GuildSettingsTransformer;
import com.pinewoodbuilders.utilities.MentionableUtil;
import com.pinewoodbuilders.utilities.NumberUtil;
import com.pinewoodbuilders.utilities.XeusPermissionUtil;
import com.pinewoodbuilders.utilities.menu.Paginator;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.internal.utils.PermissionUtil;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class UpdateCommand extends Command {
    private final Paginator.Builder builder;

    public UpdateCommand(Xeus avaire) {
        super(avaire);
        builder = new Paginator.Builder().setColumns(1).setFinalAction(m -> {
            try {
                m.delete().queue();
            } catch (PermissionException ignore) {
            }
        }).setItemsPerPage(25).waitOnSinglePage(false).useNumberedItems(false).showPageNumbers(false).wrapPageEnds(true)
            .setEventWaiter(avaire.getWaiter()).setTimeout(5, TimeUnit.MINUTES);
    }

    @Override
    public String getName() {
        return "Update Command";
    }

    @Override
    public String getDescription() {
        return "Update a user in the discord.";
    }

    @Override
    public List <String> getUsageInstructions() {
        return Arrays.asList("`:command <user>` - Update a user in the discord.");
    }

    @Override
    public List <String> getExampleUsage() {
        return Arrays.asList("`:command @Stefano#7366`");
    }

    @Override
    public List <String> getMiddleware() {
        return Arrays.asList("isGuildHROrHigher");
    }

    @Override
    public List <String> getTriggers() {
        return Collections.singletonList("update");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        if (args.length == 0) {
            context.makeError("Who/whom would you like to verify?").queue();
            return false;
        }

        return switch (args[0].toLowerCase()) {
            case "everyone", "all", "guild" -> updateEveryone(context);
            case "role" -> updateMembersWithRole(context, args);
            default -> updateMember(context, args);
        };
    }

    private boolean updateMembersWithRole(CommandMessage context, String[] args) {
        int level = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (level < GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
            context.makeError("You got to be LGL or above to run this command.").queue();
            return false;
        }
        if (args.length < 2) {
            return sendErrorMessage(context, "What role would you like to update?");
        }

        if (verificationRunning) {
            context.makeError("Somewhere, verification is already running. Please try again later...").queue();
            return false;
        }

        String roleName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Role role = context.getGuild().getRolesByName(roleName, true).stream().findFirst()
            .orElse(NumberUtil.isNumeric(roleName) ? context.getGuild().getRoleById(roleName) : null);
        if (role == null) {return sendErrorMessage(context, "That role does not exist.");}


        List<Member> members = context.getGuild().getMembersWithRoles(role);


        context.makeWarning(
                "Running verification...\nDepending on the role total members (:members) this might take a while. You will be mentioned once the time is over.")
            .set("members", members.size()).queue();
        AtomicInteger count = new AtomicInteger();

        GuildSettingsTransformer settings = context.getGuildSettingsTransformer();
        if (settings == null) {
            context.makeError("Guild settings not found.").queue();
            return false;
        }

        verificationRunning = true;
        HashMap <Member, String> ignoredMembers = new HashMap <>();

        Bucket bucket = Bucket.builder()
            .addLimit(Bandwidth.classic(45, Refill.intervally(45, Duration.ofMinutes(1))))
            .build();

        for (Member member : members) {

            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                try {
                    long timeUntilRefill = probe.getNanosToWaitForReset();
                    long refillTime = timeUntilRefill != 0L ? timeUntilRefill : 0;
                    long durationInMs = TimeUnit.MILLISECONDS.convert(refillTime, TimeUnit.NANOSECONDS);
                    long durationInSeconds = TimeUnit.SECONDS.convert(refillTime, TimeUnit.NANOSECONDS);

                    TimeUnit.NANOSECONDS.sleep(refillTime);
                } catch (InterruptedException ignored) {
                    context.makeError("Rate-limit interrupted, update cancelled.").queue();
                    return false;
                }
            }

            updateMember(context, count, settings, ignoredMembers, member);

        }
        context.getChannel().sendMessage(context.getMember().getAsMention())
            .setEmbeds(context.makeSuccess("All members have been updated").buildEmbed()).queue();

        List <String> failedMembers = new ArrayList <>();
        ignoredMembers.forEach((m, r) -> {
            failedMembers.add("`" + m.getEffectiveName() + "` - **" + r + "**");
        });

        verificationRunning = false;
        builder.setText("All members that failed to update:").setItems(failedMembers);
        builder.build().paginate(context.getChannel(), 0);
        return !verificationRunning;
    }

    private boolean updateMember(CommandMessage context, String[] args) {
        User u = MentionableUtil.getUser(context, args);
        if (u != null) {
            Member m = context.guild.getMember(u);
            if (m != null) {
                VerificationEntity verificationEntity = avaire.getRobloxAPIManager().getVerification()
                    .fetchInstantVerificationWithBackup(u.getId());
                if (verificationEntity != null) {
                    return avaire.getRobloxAPIManager().getVerification().verify(context, m);
                } else {
                    context.makeError(
                        "This user is not verified in any database. Please ask him/her to verify with `!verify`")
                        .queue();
                }
            } else {
                context.makeError("Member not found.").queue();
            }
        } else {
            context.makeError("User not found.").queue();
        }
        return false;
    }

    boolean verificationRunning;


    private boolean updateEveryone(CommandMessage context) {
        int level = XeusPermissionUtil.getPermissionLevel(context).getLevel();
        if (level < GuildPermissionCheckType.LOCAL_GROUP_LEADERSHIP.getLevel()) {
            context.makeError("You got to be LGL or above to run this command.").queue();
            return false;
        }

        if (verificationRunning) {
            context.makeError("Somewhere, verification is already running. Please try again later...").queue();
            return false;
        }


        context.makeWarning(
                "Running verification...\nDepending on the member total (:members) this might take a while. You will be mentioned once the time is over.")
            .set("members", context.getGuild().getMembers().size()).queue();
        AtomicInteger count = new AtomicInteger();

        GuildSettingsTransformer settings = context.getGuildSettingsTransformer();
        if (settings == null) {
            context.makeError("Guild settings not found.").queue();
            return false;
        }

        verificationRunning = true;
        HashMap <Member, String> ignoredMembers = new HashMap <>();

        Bucket bucket = Bucket.builder()
            .addLimit(Bandwidth.classic(45, Refill.intervally(45, Duration.ofMinutes(1))))
            .build();

        for (Member member : context.getGuild().getMembers()) {

            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                try {
                    long timeUntilRefill = probe.getNanosToWaitForReset();
                    long refillTime = timeUntilRefill != 0L ? timeUntilRefill : 0;
                    long durationInMs = TimeUnit.MILLISECONDS.convert(refillTime, TimeUnit.NANOSECONDS);
                    long durationInSeconds = TimeUnit.SECONDS.convert(refillTime, TimeUnit.NANOSECONDS);

                    TimeUnit.NANOSECONDS.sleep(refillTime);
                } catch (InterruptedException ignored) {
                    context.makeError("Rate-limit interrupted, update cancelled.").queue();
                    return false;
                }
            }

            updateMember(context, count, settings, ignoredMembers, member);

        }
        context.getChannel().sendMessage(context.getMember().getAsMention())
            .setEmbeds(context.makeSuccess("All members have been updated").buildEmbed()).queue();

        List <String> failedMembers = new ArrayList <>();
        ignoredMembers.forEach((m, r) -> {
            failedMembers.add("`" + m.getEffectiveName() + "` - **" + r + "**");
        });

        verificationRunning = false;
        builder.setText("All members that failed to update:").setItems(failedMembers);
        builder.build().paginate(context.getChannel(), 0);
        return !verificationRunning;
    }

    private void updateMember(CommandMessage context, AtomicInteger count, GuildSettingsTransformer settings, HashMap <Member, String> ignoredMembers, Member member) {
        if (member == null) {
            return;
        }

        if (member.getUser().isBot()) {
            ignoredMembers.put(member, "Account is a bot, ignored.");
            return;
        }

        if (!PermissionUtil.canInteract(context.guild.getSelfMember(), member)) {
            // ignoredMembers.put(member, "Cannot interact with user");
            return;
        }

        if (member.getRoles().stream().anyMatch(
            r -> r.getName().equalsIgnoreCase("Xeus Bypass") || r.getName().equalsIgnoreCase("RoVer Bypass"))) {
            ignoredMembers.put(member, " has the Xeus/RoVer bypass role, this user cannot be verified/updated.");
            return;
        }


        VerificationResult result = avaire.getRobloxAPIManager().getVerification().verify(settings, member, context.guild, true);
        if (!result.isSuccess()) {
            ignoredMembers.put(member, result.getMessage());
        }
        count.incrementAndGet();

        if (count.get() % 100 == 0) {
            context.makeInfo("`" + count.get() + "`/`" + context.getGuild().getMembers().size() + "`").queue();
        }
    }


}
