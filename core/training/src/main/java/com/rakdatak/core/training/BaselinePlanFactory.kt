package com.rakdatak.core.training

import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPhaseType
import com.rakdatak.core.training.model.WorkoutPlan

/**
 * Eight-week baseline for an absolute beginner.
 *
 * The progression follows the general Couch-to-5K run/walk pattern, while Rakdatak's adaptive
 * engine is allowed to repeat, step back, or accelerate sessions based on the user's response.
 * The last week reaches 30 minutes continuous running; if 5 km is not yet achieved, the app
 * continues with a distance-focused extension rather than declaring the program complete.
 */
object BaselinePlanFactory {

    private const val DEFAULT_WARM_UP_SECONDS = 180
    private const val DEFAULT_COOL_DOWN_SECONDS = 180

    fun create(
        warmUpSeconds: Int = DEFAULT_WARM_UP_SECONDS,
        coolDownSeconds: Int = DEFAULT_COOL_DOWN_SECONDS,
    ): List<WorkoutPlan> = buildList {
        repeat(3) { session ->
            add(
                plan(
                    week = 1,
                    session = session + 1,
                    title = "بداية هادئة",
                    warmUpSeconds = warmUpSeconds,
                    coolDownSeconds = coolDownSeconds,
                    main = alternating(run = 60, walk = 90, repeats = 7) + run(60),
                )
            )
        }

        repeat(3) { session ->
            add(
                plan(
                    week = 2,
                    session = session + 1,
                    title = "نبني الإيقاع",
                    warmUpSeconds = warmUpSeconds,
                    coolDownSeconds = coolDownSeconds,
                    main = alternating(run = 90, walk = 120, repeats = 5) + run(90),
                )
            )
        }

        repeat(3) { session ->
            add(
                plan(
                    week = 3,
                    session = session + 1,
                    title = "ركض أطول بهدوء",
                    warmUpSeconds = warmUpSeconds,
                    coolDownSeconds = coolDownSeconds,
                    main = listOf(
                        run(90), walk(90), run(180), walk(180),
                        run(90), walk(90), run(180),
                    ),
                )
            )
        }

        repeat(3) { session ->
            add(
                plan(
                    week = 4,
                    session = session + 1,
                    title = "ثبات أكثر",
                    warmUpSeconds = warmUpSeconds,
                    coolDownSeconds = coolDownSeconds,
                    main = listOf(
                        run(180), walk(90), run(300), walk(150),
                        run(180), walk(90), run(300),
                    ),
                )
            )
        }

        add(
            plan(
                week = 5,
                session = 1,
                title = "نزيد وقت الركض",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(300), walk(180), run(300), walk(180), run(300)),
            )
        )
        add(
            plan(
                week = 5,
                session = 2,
                title = "ثمان دقائق بثبات",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(480), walk(300), run(480)),
            )
        )
        add(
            plan(
                week = 5,
                session = 3,
                title = "أول عشرين دقيقة",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(1_200)),
            )
        )

        add(
            plan(
                week = 6,
                session = 1,
                title = "نثبت التحمل",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(300), walk(180), run(480), walk(180), run(300)),
            )
        )
        add(
            plan(
                week = 6,
                session = 2,
                title = "عشر دقائق مرتين",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(600), walk(180), run(600)),
            )
        )
        add(
            plan(
                week = 6,
                session = 3,
                title = "خمسة وعشرون دقيقة",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(1_500)),
            )
        )

        repeat(3) { session ->
            add(
                plan(
                    week = 7,
                    session = session + 1,
                    title = "نثبت 25 دقيقة",
                    warmUpSeconds = warmUpSeconds,
                    coolDownSeconds = coolDownSeconds,
                    main = listOf(run(1_500)),
                )
            )
        }

        add(
            plan(
                week = 8,
                session = 1,
                title = "28 دقيقة متواصلة",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(1_680)),
            )
        )
        add(
            plan(
                week = 8,
                session = 2,
                title = "30 دقيقة متواصلة",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(1_800)),
            )
        )
        add(
            plan(
                week = 8,
                session = 3,
                title = "تثبيت هدف 30 دقيقة",
                warmUpSeconds = warmUpSeconds,
                coolDownSeconds = coolDownSeconds,
                main = listOf(run(1_800)),
            )
        )
    }

    private fun plan(
        week: Int,
        session: Int,
        title: String,
        warmUpSeconds: Int,
        coolDownSeconds: Int,
        main: List<WorkoutPhase>,
    ) = WorkoutPlan(
        id = "w${week}s$session",
        week = week,
        session = session,
        titleAr = title,
        phases = listOf(warmUp(warmUpSeconds)) + main + listOf(coolDown(coolDownSeconds)),
    )

    private fun alternating(run: Int, walk: Int, repeats: Int): List<WorkoutPhase> = buildList {
        repeat(repeats) {
            add(run(run))
            add(walk(walk))
        }
    }

    private fun warmUp(seconds: Int) = WorkoutPhase(WorkoutPhaseType.WARM_UP, seconds)
    private fun run(seconds: Int) = WorkoutPhase(WorkoutPhaseType.RUN, seconds)
    private fun walk(seconds: Int) = WorkoutPhase(WorkoutPhaseType.WALK, seconds)
    private fun coolDown(seconds: Int) = WorkoutPhase(WorkoutPhaseType.COOL_DOWN, seconds)
}
