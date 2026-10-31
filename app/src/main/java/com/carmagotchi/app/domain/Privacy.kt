package com.carmagotchi.app.domain

/** 성장과 일반 추억은 남기고 장소를 식별할 수 있는 저장 정보를 지운다. 수집도 함께 끈다. */
fun PetState.withoutPlaces() = copy(
    locationOn = false,
    places = emptyMap(),
    trips = trips.map { it.copy(place = null) },
    pendingTrip = pendingTrip?.copy(place = null),
    memory = memory.filterNot { it.kind == "NEW_PLACE" },
    ledger = ledger.filterNot { it.contains("|SNACK:REGION:") }.toSet(),
)
