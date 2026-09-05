package com.benji.titlestudio.title.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class TitleStudioHistory {

    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_HISTORY = 80;
    private static final int COMMIT_DELAY_TICKS = 8;
    private static final Map<String, State> STATES = new HashMap<>();

    private TitleStudioHistory() {
    }

    public static void watch(TitleStudioProject project) {
        if (project == null) return;
        project.normalize();
        State state = state(project);
        String snapshot = GSON.toJson(project);

        if (state.current == null) {
            state.current = snapshot;
            return;
        }

        if (snapshot.equals(state.current)) {
            state.pending = null;
            state.pendingTicks = 0;
            return;
        }

        if (!snapshot.equals(state.pending)) {
            state.pending = snapshot;
            state.pendingTicks = 0;
            return;
        }

        state.pendingTicks++;
        if (state.pendingTicks >= COMMIT_DELAY_TICKS) commitPending(state);
    }

    public static TitleStudioProject undo(TitleStudioProject project) {
        if (project == null) return null;
        State state = state(project);
        syncCurrent(state, project);
        if (state.undo.isEmpty()) return null;

        state.redo.push(state.current);
        state.current = state.undo.pop();
        state.pending = null;
        state.pendingTicks = 0;
        return decode(state.current);
    }

    public static TitleStudioProject redo(TitleStudioProject project) {
        if (project == null) return null;
        State state = state(project);
        syncCurrent(state, project);
        if (state.redo.isEmpty()) return null;

        state.undo.push(state.current);
        trim(state.undo);
        state.current = state.redo.pop();
        state.pending = null;
        state.pendingTicks = 0;
        return decode(state.current);
    }

    public static void checkpoint(TitleStudioProject project) {
        if (project == null) return;
        syncCurrent(state(project), project);
    }

    private static State state(TitleStudioProject project) {
        return STATES.computeIfAbsent(project.workspace_id, ignored -> new State());
    }

    private static void syncCurrent(State state, TitleStudioProject project) {
        String snapshot = GSON.toJson(project);
        if (state.current == null) {
            state.current = snapshot;
            return;
        }
        if (!snapshot.equals(state.current)) {
            state.pending = snapshot;
            commitPending(state);
        }
    }

    private static void commitPending(State state) {
        if (state.pending == null || state.pending.equals(state.current)) {
            state.pending = null;
            state.pendingTicks = 0;
            return;
        }

        state.undo.push(state.current);
        trim(state.undo);
        state.current = state.pending;
        state.pending = null;
        state.pendingTicks = 0;
        state.redo.clear();
    }

    private static void trim(Deque<String> deque) {
        while (deque.size() > MAX_HISTORY) deque.removeLast();
    }

    private static TitleStudioProject decode(String json) {
        TitleStudioProject project = GSON.fromJson(json, TitleStudioProject.class);
        if (project != null) project.normalize();
        return project;
    }

    private static final class State {
        final Deque<String> undo = new ArrayDeque<>();
        final Deque<String> redo = new ArrayDeque<>();
        String current;
        String pending;
        int pendingTicks;
    }
}
