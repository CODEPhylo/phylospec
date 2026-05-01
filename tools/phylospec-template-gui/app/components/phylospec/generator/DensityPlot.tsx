'use client'

import { useEffect, useRef } from 'react'
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  Filler,
} from 'chart.js'

Chart.register(LineController, LineElement, PointElement, LinearScale, Filler)

const BINS = 100

type DensityPlotProps = {
  densityFn: (x: number) => number
  xRange: [number, number]
}

function fmtTick(value: number | string): string {
  const n = Number(value)
  if (!Number.isFinite(n)) return ''
  if (n === 0) return '0'
  const abs = Math.abs(n)
  if (abs >= 10000 || (abs < 0.01 && abs > 0)) return n.toPrecision(2)
  return parseFloat(n.toPrecision(3)).toString()
}

export function DensityPlot({ densityFn, xRange }: DensityPlotProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const chartRef = useRef<Chart | null>(null)

  useEffect(() => {
    if (!canvasRef.current) return
    const accentColor = getComputedStyle(document.documentElement)
      .getPropertyValue('--accent')
      .trim()
    const ctx = canvasRef.current.getContext('2d')!
    const gradient = ctx.createLinearGradient(0, 0, 0, 160)
    gradient.addColorStop(0, `${accentColor}30`)
    gradient.addColorStop(1, `${accentColor}00`)
    chartRef.current = new Chart(canvasRef.current, {
      type: 'line',
      data: {
        datasets: [
          {
            data: [],
            borderColor: accentColor,
            borderWidth: 1.5,
            pointRadius: 0,
            fill: true,
            backgroundColor: gradient,
            tension: 0.3,
          },
        ],
      },
      options: {
        animation: false,
        events: [],
        plugins: {
          legend: { display: false },
          tooltip: { enabled: false },
        },
        scales: {
          x: {
            type: 'linear',
            display: true,
            ticks: {
              maxTicksLimit: 6,
              font: { size: 9 },
              color: '#9ca3af',
              callback: fmtTick,
            },
            grid: { display: false },
            border: { display: false },
          },
          y: { type: 'linear', display: false },
        },
        layout: { padding: { left: 2, right: 2 } },
        responsive: true,
        maintainAspectRatio: false,
      },
    })
    return () => {
      chartRef.current?.destroy()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    if (!chartRef.current) return
    const [xMin, xMax] = xRange
    const step = (xMax - xMin) / (BINS - 1)
    const points = Array.from({ length: BINS }, (_, i) => {
      const x = xMin + i * step
      const y = densityFn(x)
      return { x, y: Number.isFinite(y) ? y : 0 }
    })
    const chart = chartRef.current
    chart.data.datasets[0].data = points
    ;(chart.options.scales!.x as any).min = xMin
    ;(chart.options.scales!.x as any).max = xMax
    chart.update('none')
  }, [densityFn, xRange])

  return (
    <div className="h-full w-full">
      <canvas ref={canvasRef} />
    </div>
  )
}
