import QtQuick
import OpenNOW

QtObject {
    property QtObject client: QtObject {
        property string state: "ready"
        property string lastError: ""
        property int sequence: 0
        property var requests: []
        signal responseReceived(string requestId, var result)
        signal requestFailed(string requestId, string code, string message)
        signal eventReceived(string name, var payload)
        function request(method, params, timeout) {
            const id = "store-test-" + (++sequence)
            requests.push({id:id, method:method, params:params})
            return id
        }
        function cancel(id) { requestFailed(id, "cancelled", "Cancelled"); return true }
    }
    function check(ok, message) { if (!ok) throw new Error("Store pagination: " + message) }
    function game(id) {
        return {id:id, uuid:id, title:"Test game " + id, imageUrl:"", heroImageUrl:"",
            variants:[{id:id, store:"Steam", inLibrary:false}], availableStores:["Steam"], genres:[]}
    }
    function page(games, cursor, more) {
        return {games:games, totalCount:2500, nextCursor:cursor, hasNextPage:more}
    }
    function run(screen, status, retry, emptyStatus) {
        ShellStore.authSession = {user:{userId:"store-fixture", displayName:"Store Test"}}
        ShellStore.reloadStoreForSession()
        const first = ShellStore.storeRequestId
        check(client.requests.find(r => r.id === first).params.limit === 100, "unbounded page requested")
        client.responseReceived(first, page([game("a"),game("b")], "cursor-one", true))
        check(ShellStore.storeGames.length === 2 && ShellStore.storeLoading, "first page not shown while loading")
        check(status.text.indexOf("2") >= 0, "progress not visible")
        ShellStore.requestStorePage()
        const second = ShellStore.storeRequestId
        check(client.requests.find(r => r.id === second).params.cursor === "cursor-one", "cursor not forwarded")
        client.requestFailed(second, "upstream_error", "Store HTTP 503")
        check(ShellStore.storeGames.length === 2 && status.text.indexOf("HTTP 503") >= 0, "partial games/error lost")
        retry.clicked()
        const retryId = ShellStore.storeRequestId
        check(client.requests.find(r => r.id === retryId).params.cursor === "cursor-one", "retry skipped failed page")
        client.responseReceived(retryId, page([game("b"),game("c")], "cursor-two", true))
        check(ShellStore.storeGames.length === 3, "overlapping pages not deduplicated")
        ShellStore.requestStorePage()
        client.responseReceived(ShellStore.storeRequestId, page([], "", false))
        check(!ShellStore.storeHasMore && !ShellStore.storeLoading && ShellStore.storeGames.length === 3, "final page did not settle")
        client.requestFailed("unrelated", "other_error", "Unrelated account error")
        check(ShellStore.storeError === "", "unrelated error leaked into Store")

        const presentation = ShellStore.storePresentationRequestId
        client.requestFailed(presentation, "upstream_error", "Banner unavailable")
        check(ShellStore.storeGames.length === 3 && ShellStore.storeWarning.indexOf("Banner unavailable") >= 0, "chrome failure broke games")

        ShellStore.refreshStore("first search")
        const stale = ShellStore.storeRequestId
        const stalePresentation = ShellStore.storePresentationRequestId
        ShellStore.refreshStore("second search")
        client.responseReceived(stale, page([game("stale")], "", false))
        if (stalePresentation) client.responseReceived(stalePresentation, {section:"panels", items:[{id:"stale"}]})
        check(ShellStore.storeGames.length === 0, "cancelled search mixed results")
        const search = ShellStore.storeRequestId
        check(client.requests.find(r => r.id === search).params.searchQuery === "second search", "search context lost")
        client.requestFailed(search, "graphql_error", "GraphQL dependency failed")
        check(emptyStatus.text === "GraphQL dependency failed", "initial failure hidden")
        ShellStore.retryStore()
        client.responseReceived(ShellStore.storeRequestId, page([game("fresh")], "repeat", true))
        ShellStore.requestStorePage()
        client.responseReceived(ShellStore.storeRequestId, page([game("wrong")], "repeat", true))
        check(ShellStore.storeState === "error" && ShellStore.storeGames.length === 1, "repeated cursor continued")

        // An account switch cancels both channels, and late results cannot leak.
        ShellStore.retryStore()
        const oldAccount = ShellStore.storeRequestId
        const oldChrome = ShellStore.storePresentationRequestId
        ShellStore.authSession = null
        ShellStore.reloadStoreForSession()
        client.responseReceived(oldAccount, page([game("private")], "", false))
        if (oldChrome) client.responseReceived(oldChrome, {section:"marquee", items:[{title:"private"}]})
        check(ShellStore.storeGames.length === 0 && ShellStore.storeMarquee.length === 0, "old account results leaked")
        check(client.requests.find(r => r.id === ShellStore.storeRequestId).method === "catalog.public.list", "signed-out path changed")
        client.responseReceived(ShellStore.storeRequestId, {games:[], totalCount:0})
        check(ShellStore.storeState === "ready" && !ShellStore.storeLoading, "empty catalog never settled")
        ShellStore.cancelStoreRequests()

        // Leave a partial-failure state for native screenshot/layout acceptance.
        ShellStore.authSession = {user:{userId:"store-fixture", displayName:"Store Test"}}
        ShellStore.reloadStoreForSession()
        const preview = []
        for (let i = 0; i < 24; ++i) preview.push(game(String(i)))
        client.responseReceived(ShellStore.storeRequestId, page(preview, "preview-next", true))
        ShellStore.requestStorePage()
        client.requestFailed(ShellStore.storeRequestId, "upstream_error", "Store HTTP 503 — the next catalog page is temporarily unavailable.")
        ShellStore.cancelStoreRequests()
        check(status.text.indexOf("24") >= 0 && retry.visible, "partial-error feedback is not actionable")
        return true
    }
}
