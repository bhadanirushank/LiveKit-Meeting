import React, { useEffect, useRef } from 'react';
import Chart from 'chart.js/auto';

export default function App() {
  const chartRef1 = useRef<HTMLCanvasElement>(null);
  const chartRef2 = useRef<HTMLCanvasElement>(null);
  const chartRef3 = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    // Render placeholder charts for visual preview
    const renderChart = (ref: React.RefObject<HTMLCanvasElement | null>, type: 'bar' | 'line' | 'doughnut', label: string) => {
      if (ref.current) {
        const existingChart = Chart.getChart(ref.current);
        if (existingChart) {
          existingChart.destroy();
        }
        const ctx = ref.current.getContext('2d');
        if (ctx) {
          new Chart(ctx, {
            type: type,
            data: {
              labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
              datasets: [{
                label: label,
                data: [12, 19, 3, 5, 2, 3],
                backgroundColor: [
                  'rgba(79, 70, 229, 0.8)',
                  'rgba(59, 130, 246, 0.8)',
                  'rgba(16, 185, 129, 0.8)',
                  'rgba(245, 158, 11, 0.8)',
                  'rgba(239, 68, 68, 0.8)',
                  'rgba(139, 92, 246, 0.8)'
                ],
                borderRadius: type === 'bar' ? 4 : 0,
                tension: 0.3
              }]
            },
            options: {
              responsive: true,
              maintainAspectRatio: false,
              plugins: { legend: { position: 'bottom' } }
            }
          });
        }
      }
    };

    renderChart(chartRef1, 'bar', 'Total Sales');
    renderChart(chartRef2, 'line', 'Traffic Over Time');
    renderChart(chartRef3, 'doughnut', 'Traffic Sources');
  }, []);

  return (
    <div className="ecv-dashboard-container h-screen w-full flex flex-col bg-slate-50 text-slate-900 font-sans p-6 gap-6 overflow-hidden" style={{ backgroundColor: '#f8fafc' }}>
      
      {/* Header section from Design HTML */}
      <div className="flex items-center justify-between bg-white/70 backdrop-blur-md border border-slate-200/50 p-4 rounded-2xl shadow-sm">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-indigo-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-indigo-200">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 3v18h18"/>
              <path d="m19 9-5 5-4-4-3 3"/>
            </svg>
          </div>
          <div className="leading-none">
            <h1 className="text-xl font-bold tracking-tight">DataFlow Pro</h1>
            <p className="text-xs text-slate-500 font-medium">Enterprise Analytics Engine</p>
          </div>
        </div>
        <div className="ecv-upload-section">
          <form id="ecv-upload-form" className="flex items-center gap-3 bg-slate-100 p-1.5 rounded-lg border border-slate-200" onSubmit={(e) => e.preventDefault()}>
            <input type="file" id="ecv_file" className="text-xs text-slate-600 file:mr-4 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-white file:text-indigo-600 hover:file:bg-indigo-50 cursor-pointer" />
            <button type="button" id="ecv-upload-btn" className="bg-indigo-600 text-white text-xs font-semibold px-4 py-1.5 rounded-md hover:bg-indigo-700 transition-colors shadow-sm shadow-indigo-100">Upload Dataset</button>
          </form>
        </div>
      </div>

      {/* 2. The DataTables Grid */}
      <div className="ecv-table-responsive flex-grow bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
        <div className="overflow-x-auto h-full">
          <table id="ecv-data-table" className="w-full text-left border-collapse">
            <thead className="bg-slate-50 border-b border-slate-200 sticky top-0 z-10">
              <tr>
                <th className="px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">ID</th>
                <th className="px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Person</th>
                <th className="px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Pages</th>
                <th className="px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Entries</th>
                <th className="px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                <th className="px-6 py-4 text-xs font-semibold text-slate-500 uppercase tracking-wider">Date</th>
              </tr>
              <tr className="ecv-filters bg-slate-50/50">
                <th className="px-6 py-2 pb-4"><input type="text" placeholder="Filter..." className="w-full text-xs p-2 rounded-md border border-slate-200 bg-white focus:ring-2 focus:ring-indigo-500/20 outline-none" /></th>
                <th className="px-6 py-2 pb-4"><input type="text" placeholder="Filter..." className="w-full text-xs p-2 rounded-md border border-slate-200 bg-white focus:ring-2 focus:ring-indigo-500/20 outline-none" /></th>
                <th className="px-6 py-2 pb-4"><input type="text" placeholder="Filter..." className="w-full text-xs p-2 rounded-md border border-slate-200 bg-white focus:ring-2 focus:ring-indigo-500/20 outline-none" /></th>
                <th className="px-6 py-2 pb-4"><input type="text" placeholder="Filter..." className="w-full text-xs p-2 rounded-md border border-slate-200 bg-white focus:ring-2 focus:ring-indigo-500/20 outline-none" /></th>
                <th className="px-6 py-2 pb-4"><input type="text" placeholder="Filter..." className="w-full text-xs p-2 rounded-md border border-slate-200 bg-white focus:ring-2 focus:ring-indigo-500/20 outline-none" /></th>
                <th className="px-6 py-2 pb-4"><input type="text" placeholder="Filter..." className="w-full text-xs p-2 rounded-md border border-slate-200 bg-white focus:ring-2 focus:ring-indigo-500/20 outline-none" /></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm">
              <tr>
                <td className="px-6 py-3 font-mono text-xs text-slate-400">#10024</td>
                <td className="px-6 py-3 font-medium">Sarah Jenkins</td>
                <td className="px-6 py-3 text-slate-600">42</td>
                <td className="px-6 py-3">1,240</td>
                <td className="px-6 py-3"><span className="px-2 py-1 rounded-full bg-emerald-100 text-emerald-700 text-[10px] font-bold uppercase">Complete</span></td>
                <td className="px-6 py-3 text-slate-500">Oct 24, 2023</td>
              </tr>
              <tr className="bg-slate-50/30">
                <td className="px-6 py-3 font-mono text-xs text-slate-400">#10025</td>
                <td className="px-6 py-3 font-medium">Mark Thompson</td>
                <td className="px-6 py-3 text-slate-600">18</td>
                <td className="px-6 py-3">854</td>
                <td className="px-6 py-3"><span className="px-2 py-1 rounded-full bg-amber-100 text-amber-700 text-[10px] font-bold uppercase">Pending</span></td>
                <td className="px-6 py-3 text-slate-500">Oct 25, 2023</td>
              </tr>
              <tr>
                <td className="px-6 py-3 font-mono text-xs text-slate-400">#10026</td>
                <td className="px-6 py-3 font-medium">Elena Rodriguez</td>
                <td className="px-6 py-3 text-slate-600">64</td>
                <td className="px-6 py-3">3,102</td>
                <td className="px-6 py-3"><span className="px-2 py-1 rounded-full bg-emerald-100 text-emerald-700 text-[10px] font-bold uppercase">Complete</span></td>
                <td className="px-6 py-3 text-slate-500">Oct 25, 2023</td>
              </tr>
            </tbody>
          </table>
          {/* Simulated DataTables pagination for preview purposes */}
          <div className="dataTables_wrapper mt-2 p-4 pt-0 flex justify-between items-center text-sm text-slate-500 flex-wrap gap-2">
            <div className="dataTables_info text-xs">Showing 1 to 3 of 3 entries</div>
            <div className="dataTables_paginate paging_simple_numbers flex gap-1 text-xs">
              <a className="paginate_button previous disabled px-3 py-1 border border-transparent rounded hover:bg-slate-100 cursor-not-allowed">Previous</a>
              <a className="paginate_button current bg-indigo-600 text-white px-3 py-1 border border-indigo-600 rounded">1</a>
              <a className="paginate_button next disabled px-3 py-1 border border-transparent rounded hover:bg-slate-100 cursor-not-allowed">Next</a>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Grid */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-6 shrink-0">
        
        {/* 3. The Custom Chart Builder UI */}
        <div className="ecv-chart-builder col-span-1 md:col-span-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between md:h-56">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-1.5 h-6 bg-indigo-500 rounded-full"></div>
            <h3 className="font-bold text-sm text-slate-800">Visualizer Control</h3>
          </div>
          <div className="ecv-builder-controls flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[10px] font-bold text-slate-400 uppercase mb-1 block">Chart Type</label>
                <select id="ecv-chart-type" defaultValue="bar" className="w-full text-xs p-2 rounded-lg border border-slate-200 bg-slate-50 outline-none focus:ring-2 focus:ring-indigo-500/20 transition-all">
                  <option value="bar">Bar Chart</option>
                  <option value="line">Line Chart</option>
                  <option value="pie">Pie Chart</option>
                </select>
              </div>
              <div>
                <label className="text-[10px] font-bold text-slate-400 uppercase mb-1 block">X-Axis</label>
                <select id="ecv-chart-x" defaultValue="date" className="w-full text-xs p-2 rounded-lg border border-slate-200 bg-slate-50 outline-none focus:ring-2 focus:ring-indigo-500/20 transition-all">
                  <option value="date">Date</option>
                  <option value="person">Person</option>
                  <option value="id">ID</option>
                </select>
              </div>
            </div>
            
            <div>
              <label className="text-[10px] font-bold text-slate-400 uppercase mb-1 block">Metrics (Y-Axis)</label>
              {/* Simulated Select2 appearance */}
              <div id="ecv-chart-y" className="flex items-center gap-2 border border-slate-200 bg-slate-50 p-2 rounded-lg min-h-[38px] flex-wrap">
                <span className="bg-indigo-600 text-white text-[10px] px-2 py-0.5 rounded-md flex items-center gap-1">
                  <span className="cursor-pointer hover:text-indigo-200">&times;</span> Pages
                </span>
                <span className="bg-indigo-600 text-white text-[10px] px-2 py-0.5 rounded-md flex items-center gap-1">
                  <span className="cursor-pointer hover:text-indigo-200">&times;</span> Entries
                </span>
              </div>
            </div>

            <div className="flex items-center justify-between mt-1">
              <label htmlFor="ecv-chart-stack" className="flex items-center gap-2 cursor-pointer group">
                <input type="checkbox" id="ecv-chart-stack" className="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500/20" /> 
                <span className="text-xs font-medium text-slate-600 group-hover:text-slate-900 transition-colors">Stack Data</span>
              </label>
              <button id="ecv-add-chart-btn" className="bg-slate-900 text-white text-xs font-bold px-4 py-2 rounded-lg hover:bg-slate-800 shadow-lg shadow-slate-200 transition-all">+ Add Chart</button>
            </div>
          </div>
        </div>

        {/* 4. The Dynamic Charts Grid */}
        <div id="ecv-charts-grid" className="ecv-charts-grid col-span-1 md:col-span-8 grid grid-cols-1 md:grid-cols-2 gap-4 h-[400px] md:h-56">
          <div className="ecv-chart-box relative bg-white border border-slate-200 rounded-2xl shadow-sm p-4 group overflow-hidden flex flex-col">
            <div className="flex justify-between items-start mb-2">
              <h4 className="text-xs font-bold text-slate-700">Entries Performance</h4>
              <button className="ecv-chart-remove w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center text-[10px] opacity-0 group-hover:opacity-100 transition-opacity">&times;</button>
            </div>
            <div className="flex-1 relative w-full h-full min-h-[100px]">
              <canvas ref={chartRef1}></canvas>
            </div>
          </div>
          
          <div className="ecv-chart-box relative bg-white border border-slate-200 rounded-2xl shadow-sm p-4 group overflow-hidden flex flex-col">
            <div className="flex justify-between items-start mb-2">
              <h4 className="text-xs font-bold text-slate-700">Growth Trend</h4>
              <button className="ecv-chart-remove w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center text-[10px] opacity-0 group-hover:opacity-100 transition-opacity">&times;</button>
            </div>
            <div className="flex-1 relative w-full h-full min-h-[100px]">
              <canvas ref={chartRef2}></canvas>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
