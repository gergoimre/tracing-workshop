package com.workshop.common

fun amountBucket(amount: Long): String = if (amount >= 10_000) "10000_PLUS" else "BELOW_10000"
