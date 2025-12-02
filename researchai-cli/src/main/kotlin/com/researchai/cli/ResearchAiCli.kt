package com.researchai.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.researchai.cli.commands.AskCommand
import com.researchai.cli.commands.ChatCommand
import com.researchai.cli.commands.GitCommand
import com.researchai.cli.commands.InitCommand
import com.researchai.cli.commands.ReviewCommand

class ResearchAiCli : CliktCommand(
    name = "rai",
    help = "ResearchAI CLI - Chat with AI from command line"
) {
    override fun run() = Unit
}

fun main(args: Array<String>) = ResearchAiCli()
    .subcommands(
        ChatCommand(),
        InitCommand(),
        AskCommand(),
        GitCommand(),
        ReviewCommand()
    )
    .main(args)
