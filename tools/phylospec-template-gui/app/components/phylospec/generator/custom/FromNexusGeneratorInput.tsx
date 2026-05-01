"use client";

import { useContext, useEffect, useMemo } from "react";
import { TypeSelector, TypeSelectorValue } from "../../TypeSelector";
import { DefaultsContext } from "../../DefaultsContext";
import type { ComponentProps } from "../../types";
import { DEFAULT_COMPONENT_ID } from "../constants";
import {
  formatGeneratorArgLabel,
  type GeneratorArg,
  type GeneratorInputValue,
} from "../GeneratorInput";

const LITERAL_STRING_ID = "literal.string";

type FromNexusGeneratorInputProps = ComponentProps<GeneratorInputValue> & {
  args: GeneratorArg[];
  description?: string;
  collapseOptionalArgs?: boolean;
};

export function FromNexusGeneratorInput({
  value,
  onChange,
  args,
  description,
  collapseOptionalArgs = true,
}: FromNexusGeneratorInputProps) {
  const defaults = useContext(DefaultsContext);

  const sentinelDefaults = useMemo(
    () =>
      Object.fromEntries(
        args
          .filter((arg) => arg.name in defaults)
          .map((arg) => [
            arg.name,
            {
              componentId: DEFAULT_COMPONENT_ID,
              value: defaults[arg.name],
              isDistribution: false,
            },
          ]),
      ),
    [args, defaults],
  );

  useEffect(() => {
    if (Object.keys(sentinelDefaults).length === 0) return;
    const needsUpdate = Object.entries(sentinelDefaults).some(
      ([k, v]) =>
        value?.[k]?.componentId !== DEFAULT_COMPONENT_ID ||
        value?.[k]?.value !== v.value,
    );
    if (needsUpdate) onChange({ ...(value ?? {}), ...sentinelDefaults });
  }, [sentinelDefaults]);

  const visibleArgs = args.filter((arg) => !(arg.name in defaults));
  const fileArg = visibleArgs.find((a) => a.name === "file");
  const otherVisibleArgs = visibleArgs.filter((a) => a.name !== "file");

  const fileSelectorValue = value?.file ?? null;
  const displayName =
    fileSelectorValue?.componentId === LITERAL_STRING_ID &&
    typeof fileSelectorValue.value === "string"
      ? fileSelectorValue.value
      : null;

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    onChange({
      ...(value ?? {}),
      ...sentinelDefaults,
      file: {
        componentId: LITERAL_STRING_ID,
        value: f.name,
        isDistribution: false,
      },
    });
  }

  function handleArgChange(argName: string, v: TypeSelectorValue) {
    onChange({ ...(value ?? {}), ...sentinelDefaults, [argName]: v });
  }

  const hasAnyUi =
    fileArg ||
    otherVisibleArgs.length > 0 ||
    Boolean(description);

  if (!hasAnyUi) return null;

  return (
    <div className="flex flex-col gap-4">
      {description && (
        <p className="text-sm text-gray-600 italic dark:text-gray-400">
          {description}
        </p>
      )}
      {fileArg && (
        <div className="flex flex-col gap-2 rounded-xl border border-gray-200/70 bg-gradient-to-br from-gray-50/60 to-white p-4 dark:bg-gray-800/60">
          <div className="flex items-center gap-1.5">
            <span className="text-sm font-medium">File</span>
            <span className="rounded-full bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">required</span>
          </div>
          <span className="text-sm italic text-gray-600 dark:text-gray-400">
            {fileArg.description}
          </span>
          <input
            type="file"
            accept=".nex,.nexus,.nxs"
            className="text-sm file:mr-2 file:cursor-pointer file:rounded-full file:border-0 file:bg-accent/10 file:px-3 file:py-1 file:text-xs file:font-medium file:text-accent"
            onChange={handleFileChange}
          />
          {displayName && (
            <span className="text-sm text-gray-600 dark:text-gray-400">
              Selected: {displayName}
            </span>
          )}
        </div>
      )}
      {otherVisibleArgs.map((arg) => {
        const argVal = value?.[arg.name] ?? null;
        const label = formatGeneratorArgLabel(arg.name);
        const operator = argVal ? (argVal.isDistribution ? "~" : "=") : "=";
        const content = (
          <>
            <span className="text-sm italic text-gray-600 dark:text-gray-400">
              {arg.description}
            </span>
            <TypeSelector
              type={arg.type}
              value={argVal}
              onChange={(v) => handleArgChange(arg.name, v)}
              allowDistributions={true}
            />
          </>
        );

        if (!arg.required && collapseOptionalArgs) {
          return (
            <details
              key={arg.name}
              className="group rounded-xl border border-gray-200/70 bg-gradient-to-br from-gray-50/60 to-white p-4 dark:bg-gray-800/60"
            >
              <summary className="flex cursor-pointer items-center gap-1">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  strokeWidth="1.5"
                  stroke="currentColor"
                  className="size-4 text-gray-500 rotate-180 transition-transform group-open:rotate-90"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M15.75 19.5 8.25 12l7.5-7.5"
                  />
                </svg>
                <span className="text-sm font-medium">{label}</span>
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500">optional</span>
              </summary>
              <div className="mt-2 flex flex-col gap-1">{content}</div>
            </details>
          );
        }

        return (
          <div
            key={arg.name}
            className="flex flex-col gap-2 rounded-xl border border-gray-200/70 bg-gradient-to-br from-gray-50/60 to-white p-4 dark:bg-gray-800/60"
          >
            <div className="flex items-center gap-1.5">
              <span className="text-sm font-medium">{label}</span>
              <span className="text-sm text-gray-500">{operator}</span>
              {!arg.required && (
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500">optional</span>
              )}
            </div>
            {content}
          </div>
        );
      })}
    </div>
  );
}
