package com.stelliberty.android.data.backup

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WebDavListingTest {

    private fun parse(xml: String) = parseBackupList(xml.trimIndent().byteInputStream())

    @Test
    fun keepsBackupFilesSortedNewestFirst() {
        val backups = parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:" xmlns:lp1="DAV:">
              <D:response>
                <D:href>/dav/stelliberty-backups/</D:href>
                <D:propstat><D:prop>
                  <D:resourcetype><D:collection/></D:resourcetype>
                  <lp1:getlastmodified>Sat, 26 Sep 2026 09:00:00 GMT</lp1:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/stelliberty-backups/stelliberty-20260925-080000.stelliberty</D:href>
                <D:propstat><D:prop>
                  <D:resourcetype/>
                  <lp1:getlastmodified>Fri, 25 Sep 2026 00:00:00 GMT</lp1:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>https://example.com/dav/stelliberty-backups/stelliberty-20260926-080000.stelliberty</D:href>
                <D:propstat><D:prop>
                  <lp1:getlastmodified>Sat, 26 Sep 2026 00:00:00 GMT</lp1:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/stelliberty-backups/notes.txt</D:href>
                <D:propstat><D:prop>
                  <lp1:getlastmodified>Sun, 27 Sep 2026 00:00:00 GMT</lp1:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
            </D:multistatus>
            """
        )

        assertEquals(
            listOf(
                RemoteBackup("stelliberty-20260926-080000.stelliberty", Instant.parse("2026-09-26T00:00:00Z")),
                RemoteBackup("stelliberty-20260925-080000.stelliberty", Instant.parse("2026-09-25T00:00:00Z")),
            ),
            backups,
        )
    }

    @Test
    fun decodesNamesAndPutsUndatedEntriesLast() {
        val backups = parse(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/example/stelliberty-backups/Example%20a+b.stelliberty</d:href>
                <d:propstat><d:prop><oc:size>1</oc:size></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/example/stelliberty-backups/b.stelliberty</d:href>
                <d:propstat><d:prop>
                  <d:getlastmodified>Thu, 24 Sep 2026 12:00:00 GMT</d:getlastmodified>
                </d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/example/stelliberty-backups/a.stelliberty</d:href>
                <d:propstat><d:prop>
                  <d:getlastmodified>Thu, 24 Sep 2026 12:00:00 GMT</d:getlastmodified>
                </d:prop></d:propstat>
              </d:response>
            </d:multistatus>
            """
        )

        assertEquals(
            listOf("b.stelliberty", "a.stelliberty", "Example a+b.stelliberty"),
            backups.map { it.name },
        )
        assertEquals(null, backups.last().lastModified)
    }
}
