package com.outsmartis.yoke.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SpokenDateTest {

    // Sunday 11 October 2026, 09:00
    private val now = LocalDateTime.of(2026, 10, 11, 9, 0)
    private val today = now.toLocalDate()

    private fun p(s: String) = SpokenDate.parse(s, now)

    private fun check(input: String, title: String, date: LocalDate?, time: LocalTime? = null) {
        val r = p(input)
        assertEquals("title of \"$input\"", title, r.title)
        assertEquals("date of \"$input\"", date, r.date)
        assertEquals("time of \"$input\"", time, r.time)
    }

    @Test fun cardExamplePortuguese() =
        check("Ligar ao contabilista amanhã às 10", "Ligar ao contabilista", today.plusDays(1), LocalTime.of(10, 0))

    @Test fun cardExampleEnglish() =
        check("Call the accountant on Friday at 3pm", "Call the accountant", LocalDate.of(2026, 10, 16), LocalTime.of(15, 0))

    @Test fun relativeDays() {
        check("Pagar a renda hoje", "Pagar a renda", today)
        check("pagar a renda amanha", "Pagar a renda", today.plusDays(1))
        check("Ver o carro depois de amanhã", "Ver o carro", today.plusDays(2))
        check("Pay rent today", "Pay rent", today)
        check("pay rent tomorrow", "Pay rent", today.plusDays(1))
        check("Pay rent the day after tomorrow", "Pay rent", today.plusDays(2))
    }

    @Test fun portugueseWeekdaysNeedALeadIn() {
        check("Reunião na sexta", "Reunião", LocalDate.of(2026, 10, 16))
        check("Reunião na próxima terça", "Reunião", LocalDate.of(2026, 10, 13))
        check("Reunião segunda-feira", "Reunião", LocalDate.of(2026, 10, 12))
        check("Reunião no sábado", "Reunião", LocalDate.of(2026, 10, 17))
        check("Jantar domingo", "Jantar", LocalDate.of(2026, 10, 18)) // today is Sunday: next week's
        check("Entregar até quinta", "Entregar", LocalDate.of(2026, 10, 15))
    }

    @Test fun weekdayWordsThatAreNotDays() {
        check("Rever a segunda versão", "Rever a segunda versão", null)
        check("Ir à quinta do avô", "Ir à quinta do avô", null)
        check("Ler a sexta parte", "Ler a sexta parte", null)
    }

    @Test fun englishWeekdaysRollToNextWeek() {
        check("Gym on Sunday", "Gym", LocalDate.of(2026, 10, 18))
        check("Dentist next monday", "Dentist", LocalDate.of(2026, 10, 12))
    }

    @Test fun datesWithMonths() {
        check("Consulta 12 de outubro", "Consulta", LocalDate.of(2026, 10, 12))
        check("Consulta dia 3 de março", "Consulta", LocalDate.of(2027, 3, 3))
        check("Consulta 10 de outubro", "Consulta", LocalDate.of(2027, 10, 10)) // passed: next year
        check("Dentist October 12", "Dentist", LocalDate.of(2026, 10, 12))
        check("Dentist on the 12th of October", "Dentist", LocalDate.of(2026, 10, 12))
        check("Dentist 2 January 2027", "Dentist", LocalDate.of(2027, 1, 2))
    }

    @Test fun dayOfMonthOnly() {
        check("Pagar IUC dia 12", "Pagar IUC", LocalDate.of(2026, 10, 12))
        check("Pagar IUC dia 5", "Pagar IUC", LocalDate.of(2026, 11, 5))
        check("Pay the bill on the 20th", "Pay the bill", LocalDate.of(2026, 10, 20))
        check("Fechar contas dia 31", "Fechar contas", LocalDate.of(2026, 10, 31))
    }

    @Test fun portugueseTimes() {
        check("Dentista amanhã às 10h30", "Dentista", today.plusDays(1), LocalTime.of(10, 30))
        check("Dentista amanhã às 10:15", "Dentista", today.plusDays(1), LocalTime.of(10, 15))
        check("Dentista amanhã às 9 horas", "Dentista", today.plusDays(1), LocalTime.of(9, 0))
        check("Dentista amanhã às 3 da tarde", "Dentista", today.plusDays(1), LocalTime.of(15, 0))
        check("Dentista amanhã às 3", "Dentista", today.plusDays(1), LocalTime.of(15, 0))
        check("Almoço com a Ana ao meio-dia", "Almoço com a Ana", today, LocalTime.NOON)
    }

    @Test fun englishTimes() {
        check("Call mum tomorrow at 3 p.m.", "Call mum", today.plusDays(1), LocalTime.of(15, 0))
        check("Call mum tomorrow at 9:45am", "Call mum", today.plusDays(1), LocalTime.of(9, 45))
        check("Call mum tomorrow at 15:30", "Call mum", today.plusDays(1), LocalTime.of(15, 30))
        check("Call mum tomorrow at 10", "Call mum", today.plusDays(1), LocalTime.of(10, 0))
        check("Lunch at noon", "Lunch", today, LocalTime.NOON)
        check("Call mum 12am", "Call mum", today.plusDays(1), LocalTime.MIDNIGHT)
    }

    @Test fun timeAloneIsTodayOrTomorrowIfPassed() {
        check("Ligar à Ana às 8", "Ligar à Ana", today.plusDays(1), LocalTime.of(8, 0)) // 08:00 passed at 09:00
        check("Ligar à Ana às 11h", "Ligar à Ana", today, LocalTime.of(11, 0))
    }

    @Test fun timeBeforeDate() =
        check("Reunião às 10h amanhã", "Reunião", today.plusDays(1), LocalTime.of(10, 0))

    @Test fun numbersInTitlesAreNotDates() {
        for (s in listOf(
            "Ler capítulo 12", "Comprar 3 livros", "Fazer as 3 tarefas", "Buy 2 apples",
            "Finish the 1st draft", "Reservar mesa para 5 amigos", "Ver episódio 4 às escondidas",
        )) {
            val r = p(s)
            assertFalse("\"$s\" found ${r.date} ${r.time}", r.found)
            assertEquals(s, r.title)
        }
    }

    @Test fun invalidValuesAreIgnored() {
        assertNull(p("Consulta 31 de fevereiro de 2027").date)
        assertNull(p("Call at 25:00").time)
        assertNull(p("Call at 13pm").time)
    }

    @Test fun foldKeepsLength() {
        val s = "Às três, amanhã é sábado"
        assertEquals(s.length, SpokenDate.fold(s).length)
        assertEquals("as tres, amanha e sabado", SpokenDate.fold(s))
    }
}
