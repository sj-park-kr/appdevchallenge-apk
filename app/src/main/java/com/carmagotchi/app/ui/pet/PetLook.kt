package com.carmagotchi.app.ui.pet

import com.carmagotchi.app.Cue
import com.carmagotchi.app.Scene
import com.carmagotchi.app.SceneKind
import com.carmagotchi.app.domain.*

/** 3.6~3.11 눈·입·팔·소품 이름. 아래 PetLook이 한 장면을 그리는 값 전부다(3.10). */
enum class Eye { OPEN, SOFT, HALF, HAPPY, CLOSED, WIDE, SQUEEZE, SPARKLE }
enum class Mouth { SMILE, OPEN, O, FLAT, WAVY, FROWN, YAWN, MUNCH }
enum class Fx { STAR_ONE, STARS, ZZ, HEART, SPARKLE, RAIN, SWEAT, SHIVER, GRUMBLE, SMUDGE }
enum class Neck { BANDANA, HANBOK, SCARF }
enum class Badge { STAR, FRIEND }
enum class Hand { UMBRELLA, BOWL_EMPTY, BOWL_FULL, FLAG }
enum class Top { RIBBON, PARTY_HAT, CHERRY, FUR_HAT }
/** 가는 쪽: 아래로 오면 정면, 위로 가면 뒷모습(얼굴 숨김), 좌우는 얼굴이 그쪽으로 쏠린다. */
enum class Heading { FRONT, BACK, SIDE }
enum class Motion {
    HATCH, GROW, GROW_HEART, GREET, DOZE, WOBBLE, DIZZY, WOBBLE_DIZZY, SWAY, UMBRELLA, WAVE, HOP, EAT, HUNGRY, SHIVER,
    RECOVER, LOOK, STRETCH, ROLLIN, FLAG, PARTY, OUTFIT, VISITOR_IN, PET, SNACK, BLINK2, CHEEK,
}

/** 팔 자세: 왼팔·오른팔 각각 (중심 x, y, 회전, rx, ry, 몸 앞이면 true). 3.5절 표. */
class Arm(val x: Float, val y: Float, val rot: Float, val rx: Float, val ry: Float, val front: Boolean)
enum class Arms(val l: Arm, val r: Arm) {
    REST(Arm(.185f, .70f, -25f, .045f, .060f, false), Arm(.815f, .70f, 25f, .045f, .060f, false)),
    WAVE(Arm(.185f, .70f, -25f, .045f, .060f, false), Arm(.86f, .55f, 35f, .045f, .065f, false)),
    HOLD(Arm(.185f, .70f, -25f, .045f, .060f, false), Arm(.66f, .74f, -15f, .050f, .040f, true)),
    HOLD_UP(Arm(.185f, .70f, -25f, .045f, .060f, false), Arm(.70f, .66f, -40f, .050f, .040f, true)),
    HUG(Arm(.40f, .82f, -10f, .050f, .035f, true), Arm(.60f, .82f, 10f, .050f, .035f, true)),
    STRETCH(Arm(.20f, .42f, -30f, .045f, .070f, false), Arm(.80f, .42f, 30f, .045f, .070f, false)),
    BELLY(Arm(.44f, .76f, -20f, .045f, .035f, true), Arm(.56f, .76f, 20f, .045f, .035f, true)),
}

/** 겉모습에 필요한 값. 단계는 look.stageOverride가 있으면 그것이 이긴다(MO-GROW). */
data class PetSpec(
    val stage: Stage, val palette: Int, val pattern: Int, val quirk: Int,
    val look: Traits, val name: String, val days: Int,
    // 기본값은 palette·pattern에서 출발해 carLook을 안 넘기는 호출도 이전 모습 그대로다.
    val carLook: CarLook = CarLook(color = palette, pattern = pattern, cheek = if (palette == 4 || palette == 6) CheekColor.ORANGE else CheekColor.AMBER),
)

fun PetState.spec() = PetSpec(stage, appearance.palette, appearance.pattern, appearance.quirk, lockedLook, name, daysTogether, carLook())

data class PetLook(
    // 정지 프레임(= 움직임 줄이기 모드의 모습)
    val eyes: Eye = Eye.OPEN, val mouth: Mouth = Mouth.SMILE, val arms: Arms = Arms.REST,
    val tilt: Float = 0f, val squashY: Float = 1f, val finTilt: Float = 0f, val cheekAlpha: Float = .9f,
    val fx: Set<Fx> = emptySet(), val neck: Neck? = null, val back: Boolean = false, val badge: Badge? = null,
    val bandage: Boolean = false, val hand: Hand? = null, val top: Top? = null, val cake: Int = 0,
    val boots: Boolean = false, val starry: Boolean = false, val cushion: Boolean = false,
    val visitor: String? = null, val motion: Motion? = null,
    val eggClosed: Boolean = false, val crack: Boolean = false, val hl: Float = 1f,
    // 움직임이 프레임마다 채우는 값(PetMotion.kt)
    val dx: Float = 0f, val dy: Float = 0f, val wave: Float = 0f, val blink: Float = 1f,
    val lookX: Float = 0f, val lookY: Float = 0f, val wheelRad: Float = 0f,
    val handK: Float = 1f, val handRot: Float = 0f, val orbit: Float = 0f, val orbitA: Float = 1f,
    val zz: Int = 3, val sparkles: Int = 2, val sparkleK: Float = 1f, val heartK: Float = 1f,
    val rainT: Float = -1f, val bandageT: Float = 0f, val ribbonK: Float = 1f, val hatDy: Float = 0f,
    val flame: Float = 1f, val confettiT: Float = -1f, val visitorDx: Float = 0f, val visitorDy: Float = 0f,
    val stageOverride: Stage? = null, val glowR: Float = -1f, val glowA: Float = 0f, val shellTop: Float = -1f,
    val windowT: Float = 1f, val shardT: Float = -1f, val faceK: Float = 1f, val snackT: Float = -1f, val munch: Boolean = false, val growFrom: Stage? = null,
    // MO-GROW-HEART: 모이는 하트 고리(0~1, 없으면 -1)와 머리 위 퐁 하트(0~1, 없으면 -1)
    val heartRing: Float = -1f, val heartPop: Float = -1f,
    val heading: Heading = Heading.FRONT, val lamp: Boolean = false, // lamp: 깜빡이가 켜진 순간(PetMove가 채움)
)

/** 3.10 기분 구간 표 + 3.8 갈래 값. 이벤트 반응이 없을 때의 기본 얼굴. */
fun baseLook(spec: PetSpec, mood: Int): PetLook {
    val child = spec.stage >= Stage.CHILD
    val adult = spec.stage >= Stage.ADULT
    val calm = child && spec.look.temperament == Temperament.CALM
    val lively = child && spec.look.temperament == Temperament.LIVELY
    val eyes = if (calm) Eye.SOFT else Eye.OPEN
    var l = when (moodBand(mood)) {
        MoodBand.LOW -> PetLook(eyes = Eye.HALF, mouth = Mouth.FROWN, squashY = .97f, finTilt = -10f, cheekAlpha = .5f)
        MoodBand.OK -> PetLook(eyes = eyes, cheekAlpha = .9f)
        MoodBand.GOOD -> PetLook(eyes = eyes, cheekAlpha = 1f)
        MoodBand.GREAT -> PetLook(eyes = Eye.SPARKLE, mouth = Mouth.OPEN, arms = Arms.WAVE, finTilt = 6f, cheekAlpha = 1f)
    }
    l = l.copy(finTilt = (l.finTilt + if (calm) -4f else if (lively) 6f else 0f).coerceIn(-12f, 10f), hl = if (lively) 1.15f else 1f)
    if (adult) l = l.copy(
        neck = if (spec.look.roamer == Roamer.TOWN) Neck.BANDANA else null,
        back = spec.look.roamer == Roamer.EXPLORER,
        badge = when (spec.look.social) { Social.SOLO -> Badge.STAR; Social.FRIENDLY -> Badge.FRIEND; else -> null },
    )
    return l
}

/** 3.10 이벤트 반응 표. 겹침 그림은 빈 슬롯에만 얹는다. */
fun sceneLook(spec: PetSpec, mood: Int, s: Scene): PetLook {
    val b = baseLook(spec, mood)
    val arrive = s.kind == SceneKind.ARRIVE
    var l = when (s.event) {
        EventId.TRIP_END -> b.copy(eyes = Eye.CLOSED, mouth = Mouth.SMILE, tilt = 4f, fx = setOf(Fx.ZZ), motion = Motion.DOZE)
        EventId.HARD_BRAKE -> b.copy(eyes = Eye.OPEN, mouth = Mouth.SMILE, arms = Arms.REST, fx = if (s.hardBrakes >= 2) setOf(Fx.STARS, Fx.SWEAT) else setOf(Fx.STAR_ONE, Fx.SWEAT),
            motion = if (s.hardBrakes >= 2) Motion.WOBBLE_DIZZY else Motion.WOBBLE)
        EventId.SHARP_TURN -> b.copy(eyes = Eye.OPEN, mouth = Mouth.SMILE, arms = Arms.REST, tilt = 8f, motion = Motion.WOBBLE_DIZZY)
        EventId.SMOOTH_TRIP -> b.copy(eyes = Eye.HAPPY, mouth = Mouth.SMILE, arms = Arms.REST, fx = setOf(Fx.SPARKLE), motion = Motion.SWAY)
        EventId.RAIN -> b.copy(eyes = Eye.OPEN, mouth = Mouth.SMILE, arms = Arms.HOLD_UP, hand = Hand.UMBRELLA, fx = setOf(Fx.RAIN), motion = Motion.UMBRELLA)
        EventId.NIGHT -> b.copy(eyes = Eye.CLOSED, mouth = Mouth.SMILE, arms = Arms.REST, tilt = 4f, fx = setOf(Fx.ZZ), starry = true, motion = Motion.DOZE)
        EventId.PASSENGER_ON ->
            if (arrive) b.copy(eyes = Eye.OPEN, mouth = Mouth.SMILE, arms = Arms.REST, cushion = true, fx = setOf(Fx.HEART), motion = Motion.HOP)
            else b.copy(eyes = Eye.HAPPY, mouth = Mouth.OPEN, arms = Arms.WAVE, fx = setOf(Fx.HEART), motion = Motion.WAVE)
        EventId.REFUEL, EventId.RECHARGE -> b.copy(eyes = Eye.HAPPY, mouth = Mouth.SMILE, arms = Arms.BELLY, motion = Motion.EAT)
        EventId.LOW_FUEL, EventId.LOW_BATTERY -> b.copy(eyes = Eye.HALF, mouth = Mouth.O, arms = Arms.HOLD, hand = Hand.BOWL_EMPTY, fx = setOf(Fx.GRUMBLE), motion = Motion.HUNGRY)
        EventId.WARN_TPMS -> b.copy(eyes = Eye.HALF, mouth = Mouth.FROWN, arms = Arms.HUG, bandage = true, fx = setOf(Fx.SHIVER), motion = Motion.SHIVER)
        EventId.WARN_WASHER -> b.copy(eyes = Eye.HALF, mouth = Mouth.FLAT, arms = Arms.REST, bandage = true, fx = setOf(Fx.SMUDGE), motion = Motion.BLINK2)
        EventId.WARN_OIL -> b.copy(eyes = Eye.HALF, mouth = Mouth.FLAT, arms = Arms.REST, bandage = true, squashY = .96f)
        EventId.WARN_OTHER -> b.copy(eyes = Eye.HALF, mouth = Mouth.FLAT, arms = Arms.REST, bandage = Cue.CRITICAL !in s.cues)
        EventId.WARN_CLEARED -> b.copy(eyes = Eye.HAPPY, mouth = Mouth.SMILE, arms = Arms.WAVE, top = Top.RIBBON, fx = setOf(Fx.SPARKLE), motion = Motion.RECOVER)
        EventId.NEW_PLACE -> b.copy(eyes = Eye.OPEN, mouth = Mouth.SMILE, arms = Arms.REST, motion = Motion.LOOK)
        EventId.REST_STOP -> b.copy(eyes = Eye.HAPPY, mouth = Mouth.SMILE, arms = Arms.REST, fx = setOf(Fx.SPARKLE), motion = Motion.STRETCH)
        EventId.LONG_ABSENCE -> b.copy(eyes = Eye.HAPPY, mouth = Mouth.OPEN, arms = Arms.WAVE, fx = setOf(Fx.HEART), motion = Motion.ROLLIN)
        EventId.MILESTONE_KM -> b.copy(eyes = Eye.SPARKLE, mouth = Mouth.OPEN, arms = Arms.HOLD_UP, hand = Hand.FLAG, motion = Motion.FLAG)
        EventId.FIRST_OF_DAY -> b.copy(eyes = Eye.OPEN, mouth = Mouth.SMILE, motion = Motion.STRETCH)
        EventId.BIRTHDAY -> b.copy(eyes = Eye.SPARKLE, mouth = Mouth.OPEN, arms = Arms.WAVE, top = Top.PARTY_HAT, cake = 1, motion = Motion.PARTY)
        EventId.SEASON_EVENT -> b.copy(motion = Motion.OUTFIT)
        EventId.STAGE_UP -> b.copy(eyes = Eye.SPARKLE, mouth = Mouth.OPEN, arms = Arms.WAVE, fx = setOf(Fx.SPARKLE), motion = if (s.heartsFull) Motion.GROW_HEART else Motion.GROW)
        else -> if (arrive) b.copy(motion = Motion.HOP) else b.copy(arms = Arms.WAVE, motion = Motion.GREET)
    }
    if (spec.stage == Stage.EGG) l = l.copy(arms = Arms.REST, hand = null, top = null, neck = null)
    // 겹침 그림(PRD F2 4번): 빈 슬롯에만
    for (c in s.cues) l = when (c) {
        Cue.STAR -> l.copy(fx = l.fx + Fx.STAR_ONE)
        Cue.TILT -> if (l.tilt == 0f) l.copy(tilt = 8f) else l
        Cue.UMBRELLA -> if (l.hand == null && spec.stage != Stage.EGG) l.copy(hand = Hand.UMBRELLA, arms = Arms.HOLD_UP, fx = l.fx + Fx.RAIN) else l
        Cue.FUR_HAT -> if (l.top == null) l.copy(top = Top.FUR_HAT) else l
        Cue.STARRY -> l.copy(starry = true)
        Cue.CUSHION -> l.copy(cushion = true)
        Cue.BANDAGE -> l.copy(bandage = true)
        Cue.EMPTY_BOWL -> if (l.hand == null && spec.stage != Stage.EGG) l.copy(hand = Hand.BOWL_EMPTY, arms = Arms.HOLD) else l
        Cue.HANBOK -> if (spec.stage >= Stage.ADULT) l.copy(neck = Neck.HANBOK) else l
        Cue.SCARF -> if (spec.stage != Stage.EGG) l.copy(neck = Neck.SCARF) else l
        Cue.BOOTS -> l.copy(boots = true)
        Cue.CHERRY -> if (l.top == null && spec.stage != Stage.EGG) l.copy(top = Top.CHERRY) else l
        Cue.CRITICAL -> l
    }
    return l.copy(visitor = s.visitor, growFrom = s.fromStage)
}
