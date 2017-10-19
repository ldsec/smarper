function [post nlZ dnlZ] = infFITC_sKL(hyp, mean, cov, lik, x, y)
% stochastic implementation based on Scalable Variational Gaussian Process Classification


persistent last_alpha                                   % copy of the last alpha
if any(isnan(last_alpha)), last_alpha = zeros(size(last_alpha)); end   % prevent

maxit=500;
inf = 'infLaplace';

cov1 = cov{1}; if isa(cov1, 'function_handle'), cov1 = func2str(cov1); end
if ~strcmp(cov1,'covFITC'); error('Only covFITC supported.'), end    % check cov
if isfield(hyp,'xu'), cov{3} = hyp.xu; end  % hyp.xu is provided, replace cov{3}

if ~isempty(hyp.lik)                          % hard coded inducing inputs noise
  sn2 = exp(2*hyp.lik(end)); snu2 = 1e-6*sn2;               % similar to infFITC
else
  snu2 = 1e-6;
end
lik_name = func2str(lik{1});
rot180   = @(A)   rot90(rot90(A));                     % little helper functions

x_batch=x;
y_batch=y;
[diagK_batch,Kuu_batch,Ku_batch] = feval(cov{:}, hyp.cov, x_batch);         % evaluate covariance matrix
nu = size(Kuu_batch,1);
Kuu_batch=Kuu_batch+snu2*eye(nu);
R = chol(Kuu_batch); 
chol_inv = @(A) rot180(chol(rot180(A))')\eye(nu);                 % chol(inv(A))
R0 = chol_inv(Kuu_batch);           % initial R, used for refresh O(nu^3)
V_batch= R0*Ku_batch; d0_batch = diagK_batch-sum(V_batch.*V_batch,1)';
const_term=-0.5*nu+sum(log(diag(R)));
m_0 = feval(mean{:}, hyp.mean, cov{3}); 
m_u = m_0;
C_u = chol(Kuu_batch,'lower');
C_u = C_u-diag(diag(C_u))+diag(log(diag(C_u)));


n_batch=size(x_batch,1);
%mini_batch_rate=0.1;
%mini_batch_size=floor(n_batch*mini_batch_rate);
mini_batch_size=hyp.mini_batch_size;
assert(mini_batch_size>0)
mini_batch_num=ceil(n_batch/mini_batch_size);


max_pass = hyp.max_pass; 
momentum_m_u=zeros(nu,1);
momentum_C_u=zeros(nu,nu);
momentum=hyp.momentum;

pass=0;
rng(1);
iter=0;
while pass<max_pass
	index=randperm(n_batch);

	offset=0;
	mini_batch_counter=0;
	debug_count=0;
	while mini_batch_counter<mini_batch_num
		C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));
		nlZ_batch=batch_nlz_sparse(mean, lik, x_batch,  y_batch,  hyp, R0, Ku_batch, m_0, diagK_batch, R, C, m_u);
		nlZ_batch2=batch_nlz_sparsev3(mean, lik, x_batch,  y_batch,  hyp, Ku_batch, m_0,  C, m_u, Kuu_batch, d0_batch, const_term);

		fprintf('%d) %.4f %.4f\n', iter, nlZ_batch, nlZ_batch2);
		iter = iter + 1;


		to_idx=(mini_batch_counter+1)*mini_batch_size;
		if to_idx>n_batch
			to_idx=n_batch;
		end
		from_idx=mini_batch_counter*mini_batch_size+1;
		x=x_batch(index(from_idx:to_idx),:);
		y=y_batch(index(from_idx:to_idx));



		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		%mini_batch
		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		[diagK,Kuu,Ku] = feval(cov{:}, hyp.cov, x);         % evaluate covariance matrix
		[n, D] = size(x);
		m = feval(mean{:}, hyp.mean, x);                      % evaluate the mean vector
		V = R0*Ku; d0 = diagK-sum(V.*V,1)';    % initial d, needed for refresh O(n*nu^2)
		H=Kuu_batch\Ku;

		nlZ_mini_batch=Inf;
		it = 0;   

		rate=hyp.learning_rate;
		%while it<maxit
			alpha=Kuu_batch\(m_u-m_0);
			post_m=m+Ku'*alpha;

			C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));

			V1=C'*H;
			post_v=d0+sum(V1.*V1,1)';

			if hyp.stochastic_approx==1
				[ll, df, dv] = sampling_E(y, post_m, post_v, lik, hyp.sample_size, hyp.lik);
			else
				switch lik_name
				case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
					[ll, df, dv] = E_log_p(lik_name, y, post_m, post_v, hyp.lik);
				otherwise	 
					[ll,df,d2f,dv] = likKL(post_v, lik, hyp.lik, y, post_m);
				end
			end

			weight=(double(n_batch) ./ size(x,1));
			df=df .* weight;
			dv=dv .* weight;

			tmp2=R*alpha;
			%it
			%nlZ_mini_batch=-sum(ll)+0.5*(trace(tmp1*tmp1')+tmp2'*tmp2)-sum(log(diag(C)))+const_term

			g_rate=rate;

			g_m_u=alpha-H*df;
			momentum_m_u=momentum*momentum_m_u-g_rate*g_m_u;
			m_u=m_u+momentum_m_u;
			%m_u=m_u-g_rate*g_m_u;


			%g_C_u=diag(-1.0./diag(C))+tril(Kuu_batch\C - H*diag(2.0*dv)*H'*C);
			%g_C_u=g_C_u-diag(diag(g_C_u)) +diag( diag(g_C_u) .* diag(C) );
			g_C_u=tril(Kuu_batch\C - H*diag(2.0*dv)*H'*C);
			g_C_u=g_C_u-diag(diag(g_C_u)) +diag( diag(g_C_u) .* diag(C) )+ diag(-1 .* ones(nu, 1));


			momentum_C_u=momentum*momentum_C_u-g_rate*g_C_u;
			C_u=C_u+momentum_C_u;
			%C_u=C_u-g_rate*g_C_u;

			%it=it+1;
		%end
		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

		debug_count=debug_count+size(x,1);
		mini_batch_counter=mini_batch_counter+1;
	end
	assert(debug_count==n_batch);
	pass=pass+1;
end

C = C_u-diag(diag(C_u))+diag(exp(diag(C_u)));
alpha=Kuu_batch\(m_u-m_0);
tmp1=C'*R0';
post.alpha=alpha;
post.L=R0'*(tmp1'*tmp1-eye(nu))*R0;
post.sW=zeros(n);


nlZ=batch_nlz_sparse(mean, lik, x_batch,  y_batch,  hyp,  R0, Ku_batch, m_0, diagK_batch, R, C, m_u);
fprintf('final: %.4f\n', nlZ);

if nargout>2                                           % do we want derivatives?
  warning('to be implemented\n');
  dnlZ = NaN;
end
