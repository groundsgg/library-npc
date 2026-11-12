package example;

import gg.grounds.npc.minestom.*;
import java.util.IdentityHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minestom.server.entity.Player;

/** Demo ownership only; rendering, expiry, clock and parent cleanup stay in the library. */
final class NpcDemoPersonal {
    private final NpcHandle parent;
    private final Function<Player,NpcPresentationOptions> options;
    private final IdentityHashMap<Player,Session> sessions = new IdentityHashMap<>();
    private static final class Session {
        final AtomicBoolean closing = new AtomicBoolean();
        final CompletableFuture<NpcPresentationStart> start = new CompletableFuture<>();
        final CompletableFuture<NpcPresentationHandle> ready = new CompletableFuture<>();
    }
    NpcDemoPersonal(NpcHandle parent, Function<Player,NpcPresentationOptions> options) {
        this.parent=parent; this.options=options;
    }
    CompletionStage<NpcPresentationHandle> open(Player player) {
        var session = new Session();
        synchronized(sessions) {
            if(sessions.containsKey(player)) return CompletableFuture.failedFuture(new IllegalStateException("Close your current presentation first"));
            sessions.put(player,session);
        }
        try {
            var start = NpcPresentations.prepare(parent,options.apply(player));
            session.start.complete(start);
            start.ready().thenCompose(result -> {
                if(session.closing.get()) return CompletableFuture.<NpcPresentationHandle>failedFuture(new CancellationException("Demo closed"));
                if(!(result instanceof NpcPresentationResult.Ready ready))
                    return CompletableFuture.<NpcPresentationHandle>failedFuture(new IllegalStateException(result.toString()));
                var handle=ready.getHandle();
                handle.closed().whenComplete((unused,error) -> forget(player,session));
                return handle.publish().thenApply(unused -> handle);
            }).whenComplete((handle,error) -> {
                if(error==null && !session.closing.get()) session.ready.complete(handle);
                else {
                    start.cancel().whenComplete((unused,cleanup) -> {
                        forget(player,session);
                        session.ready.completeExceptionally(error!=null ? error : new CancellationException("Demo closed"));
                    });
                }
            });
        } catch(Throwable error) {
            session.start.completeExceptionally(error);session.ready.completeExceptionally(error);forget(player,session);
        }
        return session.ready.minimalCompletionStage();
    }
    CompletionStage<Void> with(Player player, Function<NpcPresentationHandle,CompletionStage<Void>> action) {
        Session session;
        synchronized(sessions) {session=sessions.get(player);}
        return session==null ? CompletableFuture.failedFuture(new IllegalStateException("Use /personal first")) : session.ready.thenCompose(action);
    }
    CompletionStage<Void> close(Player player) {
        Session session;
        synchronized(sessions) {session=sessions.remove(player);}
        if(session==null) return CompletableFuture.completedFuture(null);
        session.closing.set(true);
        return session.start.thenCompose(NpcPresentationStart::cancel);
    }
    private void forget(Player player, Session session) {
        synchronized(sessions) {if(sessions.get(player)==session) sessions.remove(player);}
    }
}
