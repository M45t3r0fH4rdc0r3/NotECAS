package de.mohcomputer.notecas

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun exposesProductAndProgrammerNames() {
        assertEquals("NotECAS", AppIdentity.productName)
        assertEquals("Wolfgang Lindner", AppIdentity.programmerName)
    }
}
